package com.ruoyi.interview.controller;

import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import com.ruoyi.interview.controller.rest.common.RequestContextFactory;
import com.ruoyi.interview.domain.platform.DurableStreamType;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.common.core.domain.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * RuoYi 同源 SSE 边界。
 *
 * 同一 RuoYi JWT 下的有界 SSE 长连接：先 replay，再轮询 durable stream，定时 heartbeat，
 * 到达最大连接时长或客户端断开时清理任务。事件 payload 只允许公开状态字段。
 */
@RestController
@RequestMapping(path = "/api/v1/streams")
public class InterviewStreamController {
    private static final int REPLAY_LIMIT = 100;
    private static final long EMITTER_TIMEOUT_MILLIS = 5 * 60_000L;
    private static final long POLL_MILLIS = 1_000L;
    private static final long HEARTBEAT_MILLIS = 15_000L;
    private static final Set<String> PUBLIC_DATA_KEYS = Set.of(
            "state", "status", "reasonCode", "turnId", "turnSequence", "operationId",
            "snapshotVersion", "recoverable", "count");
    private final RequestContextFactory contexts;
    private final InterviewRepository interviews;
    private final DurableStreamPort streams;
    private final boolean enabled;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            daemonThreads());
    private final ConcurrentHashMap<String, StreamConnection> connections = new ConcurrentHashMap<>();

    public InterviewStreamController(RequestContextFactory contexts, InterviewRepository interviews,
                                     DurableStreamPort streams,
                                     @Value("${interview.foundation-safety.business-rest-endpoints-enabled:false}")
                                     boolean enabled) {
        this.contexts = contexts;
        this.interviews = interviews;
        this.streams = streams;
        this.enabled = enabled;
    }

    @GetMapping("/interviews/{interviewId}")
    @PreAuthorize("@ss.hasPermi('interview:session:recover')")
    public ResponseEntity<?> stream(
            @PathVariable UUID interviewId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            HttpServletRequest request) {
        var context = contexts.query(request);
        AjaxResult result = AjaxResult.error(HttpStatus.NOT_IMPLEMENTED.value(),
                "面试 SSE durable stream 尚未接入 PostgreSQL");
        if (!enabled) {
            result.put("errorCode", "INTERVIEW_STREAM_NOT_READY");
            result.put("retryable", false);
            result.put("data", Map.of("recoverableAction", "READ_SNAPSHOT"));
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(result);
        }

        ResourceId sessionId = ResourceId.of(interviewId);
        var session = interviews.findSession(context.principal().tenantId(), sessionId);
        if (session.isEmpty() || !session.orElseThrow().userId().equals(context.principal().userId())) {
            AjaxResult denied = AjaxResult.error(HttpStatus.NOT_FOUND.value(), "面试会话不存在");
            denied.put("errorCode", "INTERVIEW_NOT_FOUND");
            denied.put("retryable", false);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(denied);
        }

        String cursor = blankToNull(lastEventId);
        DurableStreamPort.ReplayResult initialReplay = null;
        String initialCurrentCursor = null;
        if (cursor != null) {
            try {
                initialReplay = streams.replayAfter(
                        context.principal().tenantId(), DurableStreamType.INTERVIEW, sessionId,
                        cursor, REPLAY_LIMIT, context.requestedAt());
                if (initialReplay.status() == DurableStreamPort.CursorStatus.EXPIRED) {
                    return gone(sessionId);
                }
                if (initialReplay.status() == DurableStreamPort.CursorStatus.UNKNOWN_OR_FOREIGN) {
                    return invalidCursor();
                }
            } catch (IllegalArgumentException exception) {
                return invalidCursor();
            } catch (DomainException exception) {
                return exception.code() == DomainErrorCode.INVALID_ARGUMENT
                        ? invalidCursor() : streamUnavailable();
            } catch (AdapterUnavailableException exception) {
                return streamUnavailable();
            } catch (RuntimeException exception) {
                return streamUnavailable();
            }
            cursor = initialReplay.nextCursor().orElse(cursor);
        } else {
            try {
                initialCurrentCursor = streams.currentCursor(
                                context.principal().tenantId(), DurableStreamType.INTERVIEW, sessionId)
                        .orElse(null);
            } catch (RuntimeException exception) {
                return streamUnavailable();
            }
        }

        SseEmitter emitter = emitter();
        StreamConnection connection = new StreamConnection(emitter, context.principal().tenantId(), sessionId, cursor);
        connections.put(connection.id(), connection);
        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> remove(connection));
        emitter.onError(error -> remove(connection));
        if (cursor == null) {
            connection.cursor = initialCurrentCursor;
            send(connection, null, "stream.ready", Map.of("snapshotUrl", snapshotUrl(sessionId)));
        } else {
            // 初始 replay 已在建立 emitter 前计算，直接发送缓存结果，避免重复查询与重复事件。
            initialReplay.events().forEach(item -> {
                send(connection, item.cursor(), item.event().type(), eventData(item));
                connection.cursor = item.cursor();
            });
        }
        heartbeat(connection);
        connection.task = scheduler.scheduleAtFixedRate(() -> poll(connection), POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    private static SseEmitter emitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MILLIS);
    }

    private static Map<String, Object> eventData(DurableStreamPort.ReplayEvent item) {
        var event = item.event();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId().value());
        payload.put("type", event.type());
        payload.put("streamId", event.streamId().value());
        payload.put("aggregateId", event.aggregateId().value());
        payload.put("aggregateVersion", event.aggregateVersion().value());
        payload.put("sequence", event.sequence());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("correlationId", event.correlationId().value());
        payload.put("durability", "DURABLE");
        LinkedHashMap<String, String> publicData = new LinkedHashMap<>();
        event.data().forEach((key, value) -> {
            if (PUBLIC_DATA_KEYS.contains(key) && value.length() <= 256) {
                publicData.put(key, value);
            }
        });
        payload.put("data", publicData);
        return payload;
    }

    private void poll(StreamConnection connection) {
        if (connection.closed || !connections.containsKey(connection.id())) return;
        if (System.currentTimeMillis() - connection.createdAt > EMITTER_TIMEOUT_MILLIS) {
            remove(connection);
            connection.emitter.complete();
            return;
        }
        try {
            if (connection.cursor != null) {
                DurableStreamPort.ReplayResult replay = streams.replayAfter(
                        connection.tenantId, DurableStreamType.INTERVIEW, connection.streamId,
                        connection.cursor, REPLAY_LIMIT, Instant.now());
                if (replay.status() == DurableStreamPort.CursorStatus.EXPIRED) {
                    send(connection, null, "stream.recovery-required", Map.of(
                            "reasonCode", "STREAM_CURSOR_EXPIRED", "snapshotUrl", snapshotUrl(connection.streamId)));
                    remove(connection);
                    connection.emitter.complete();
                    return;
                }
                if (replay.status() == DurableStreamPort.CursorStatus.UNKNOWN_OR_FOREIGN) {
                    send(connection, null, "stream.recovery-required", Map.of("reasonCode", "STREAM_CURSOR_INVALID"));
                    remove(connection);
                    connection.emitter.complete();
                    return;
                }
                replay.events().forEach(item -> {
                    send(connection, item.cursor(), item.event().type(), eventData(item));
                    connection.cursor = item.cursor();
                });
            }
            if (System.currentTimeMillis() - connection.lastHeartbeatAt >= HEARTBEAT_MILLIS) {
                heartbeat(connection);
            }
        } catch (AdapterUnavailableException exception) {
            send(connection, null, "stream.recovery-required", Map.of("reasonCode", "STREAM_UNAVAILABLE"));
            remove(connection);
            connection.emitter.complete();
        } catch (RuntimeException exception) {
            send(connection, null, "stream.recovery-required", Map.of("reasonCode", "STREAM_UNAVAILABLE"));
            remove(connection);
            connection.emitter.complete();
            remove(connection);
            connection.emitter.completeWithError(exception);
        }
    }

    private static void send(StreamConnection connection, String id, String name, Object data) {
        try {
            synchronized (connection) {
                if (connection.closed) return;
                SseEmitter.SseEventBuilder event = SseEmitter.event().name(name).reconnectTime(1_000L).data(data);
                if (id != null) event.id(id);
                connection.emitter.send(event);
            }
        } catch (IOException exception) {
            connection.closed = true;
            connection.emitter.completeWithError(exception);
        }
    }

    private static void heartbeat(StreamConnection connection) {
        try {
            synchronized (connection) {
                if (connection.closed) return;
                connection.emitter.send(SseEmitter.event().comment("heartbeat").reconnectTime(1_000L));
                connection.lastHeartbeatAt = System.currentTimeMillis();
            }
        } catch (IOException exception) {
            connection.closed = true;
            connection.emitter.completeWithError(exception);
        }
    }

    private void remove(StreamConnection connection) {
        connection.closed = true;
        connections.remove(connection.id(), connection);
        if (connection.task != null) connection.task.cancel(false);
    }

    private static ResponseEntity<AjaxResult> gone(ResourceId sessionId) {
        AjaxResult expired = AjaxResult.error(HttpStatus.GONE.value(), "SSE 游标已过期，请恢复快照");
        expired.put("errorCode", "STREAM_CURSOR_EXPIRED");
        expired.put("retryable", true);
        expired.put("data", Map.of("snapshotUrl", snapshotUrl(sessionId)));
        return ResponseEntity.status(HttpStatus.GONE).body(expired);
    }

    private static ResponseEntity<AjaxResult> invalidCursor() {
        AjaxResult invalid = AjaxResult.error(HttpStatus.BAD_REQUEST.value(), "SSE 游标无效");
        invalid.put("errorCode", "STREAM_CURSOR_INVALID");
        invalid.put("retryable", false);
        return ResponseEntity.badRequest().body(invalid);
    }

    private static ResponseEntity<AjaxResult> streamUnavailable() {
        AjaxResult unavailable = AjaxResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(), "SSE 流暂不可用");
        unavailable.put("errorCode", "STREAM_UNAVAILABLE");
        unavailable.put("retryable", true);
        unavailable.put("data", Map.of("recoverableAction", "READ_SNAPSHOT"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(unavailable);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ThreadFactory daemonThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "interview-sse-poll");
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        connections.values().forEach(this::remove);
        scheduler.shutdownNow();
    }

    private static String snapshotUrl(ResourceId sessionId) {
        return "/api/v1/interviews/" + sessionId.value();
    }

    private static final class StreamConnection {
        private final SseEmitter emitter;
        private final com.ruoyi.interview.domain.platform.TenantId tenantId;
        private final ResourceId streamId;
        private final long createdAt = System.currentTimeMillis();
        private final String id = UUID.randomUUID().toString();
        private volatile String cursor;
        private volatile long lastHeartbeatAt;
        private volatile boolean closed;
        private volatile java.util.concurrent.ScheduledFuture<?> task;

        private StreamConnection(SseEmitter emitter,
                                 com.ruoyi.interview.domain.platform.TenantId tenantId,
                                 ResourceId streamId,
                                 String cursor) {
            this.emitter = emitter;
            this.tenantId = tenantId;
            this.streamId = streamId;
            this.cursor = cursor;
        }

        private String id() { return id; }
    }
}
