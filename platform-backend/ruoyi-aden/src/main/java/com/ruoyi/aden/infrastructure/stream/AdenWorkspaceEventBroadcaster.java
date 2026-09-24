package com.ruoyi.aden.infrastructure.stream;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 进程级唤醒器；业务事件始终从 MySQL aden_event 按序 drain。 */
public final class AdenWorkspaceEventBroadcaster {
    static final String SSE_EVENT_NAME = "aden_event";
    private static final int GLOBAL_LIMIT = 128;
    private static final int USER_LIMIT = 4;
    private static final int WORKSPACE_LIMIT = 32;

    private final AdenOperatorQueryService queries;
    private final ThreadPoolTaskExecutor drainExecutor;
    private final ThreadPoolTaskExecutor sendExecutor;
    private final int replayBatchSize;
    private final int queueCapacity;
    private final long sendTimeoutSeconds;
    private final long connectionTtlMillis;
    private final EmitterFactory emitterFactory;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public AdenWorkspaceEventBroadcaster(AdenOperatorQueryService queries,
                                         ThreadPoolTaskExecutor drainExecutor,
                                         ThreadPoolTaskExecutor sendExecutor,
                                         int replayBatchSize,
                                         int queueCapacity,
                                         int sendTimeoutSeconds,
                                         int connectionTtlSeconds) {
        this(queries, drainExecutor, sendExecutor, replayBatchSize, queueCapacity,
                sendTimeoutSeconds, connectionTtlSeconds, SseEmitter::new);
    }

    AdenWorkspaceEventBroadcaster(AdenOperatorQueryService queries,
                                  ThreadPoolTaskExecutor drainExecutor,
                                  ThreadPoolTaskExecutor sendExecutor,
                                  int replayBatchSize,
                                  int queueCapacity,
                                  int sendTimeoutSeconds,
                                  int connectionTtlSeconds,
                                  EmitterFactory emitterFactory) {
        this.queries = queries;
        this.drainExecutor = drainExecutor;
        this.sendExecutor = sendExecutor;
        this.replayBatchSize = replayBatchSize;
        this.queueCapacity = queueCapacity;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
        this.connectionTtlMillis = Math.multiplyExact(connectionTtlSeconds, 1000L);
        this.emitterFactory = Objects.requireNonNull(emitterFactory, "emitterFactory");
    }

    public SseEmitter open(AdenOperatorPrincipal principal, AdenWorkspaceId workspaceId,
                           String cursor, String correlationId) {
        Objects.requireNonNull(principal);
        AdenOperatorQueryService.EventBatch initial = queries.events(
                principal, workspaceId, cursor, replayBatchSize, correlationId);
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.create(connectionTtlMillis);
        Connection connection = new Connection(id, principal, workspaceId, correlationId,
                emitter, cursor, initial);
        synchronized (connections) {
            enforceLimits(principal.userId(), workspaceId.value());
            connections.put(id, connection);
        }
        Runnable cleanup = () -> cleanup(connection);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> { cleanup.run(); emitter.complete(); });
        emitter.onError(failure -> cleanup.run());
        schedule(connection);
        return emitter;
    }

    public void wake(AdenWorkspaceId workspaceId) {
        connections.values().stream().filter(c -> c.workspaceId.equals(workspaceId)).forEach(this::schedule);
    }

    public void heartbeatAll() {
        connections.values().forEach(this::schedule);
    }

    public int connectionCount() { return connections.size(); }

    private void enforceLimits(long userId, String workspaceId) {
        long user = connections.values().stream().filter(c -> c.principal.userId() == userId).count();
        long workspace = connections.values().stream()
                .filter(c -> c.workspaceId.value().equals(workspaceId)).count();
        if (connections.size() >= GLOBAL_LIMIT || user >= USER_LIMIT || workspace >= WORKSPACE_LIMIT) {
            throw new AdenApplicationException("ADEN_RATE_LIMITED", "SSE 连接数已达到上限");
        }
    }

    private void schedule(Connection connection) {
        boolean start = false;
        boolean overflow = false;
        synchronized (connection) {
            if (connection.closed.get()) return;
            if (connection.pendingSignals >= queueCapacity) {
                overflow = true;
            } else {
                connection.pendingSignals++;
                if (!connection.scheduled.get()) {
                    connection.scheduled.set(true);
                    start = true;
                }
            }
        }
        if (overflow) {
            close(connection, new AdenApplicationException(
                    "ADEN_STREAM_UNAVAILABLE", "SSE 消费者过慢，请重新获取 bootstrap"));
            return;
        }
        if (!start) return;
        try {
            drainExecutor.execute(() -> drainLoop(connection));
        } catch (TaskRejectedException exception) {
            synchronized (connection) { connection.scheduled.set(false); }
            close(connection, exception);
        }
    }

    private void drainLoop(Connection connection) {
        try {
            while (!connection.closed.get()) {
                synchronized (connection) {
                    if (connection.pendingSignals == 0) {
                        connection.scheduled.set(false);
                        return;
                    }
                    // 唤醒只是“可能有新账本事件”的提示，可合并；业务事件仍逐条从 MySQL 重放。
                    connection.pendingSignals = 0;
                }
                drainOnce(connection);
            }
        } catch (RuntimeException exception) {
            close(connection, exception);
        } finally {
            boolean restart;
            synchronized (connection) {
                connection.scheduled.set(false);
                restart = connection.pendingSignals > 0 && !connection.closed.get();
            }
            if (restart) startQueued(connection);
        }
    }

    private void startQueued(Connection connection) {
        synchronized (connection) {
            if (connection.closed.get() || connection.scheduled.get() || connection.pendingSignals == 0) return;
            connection.scheduled.set(true);
        }
        try {
            drainExecutor.execute(() -> drainLoop(connection));
        } catch (TaskRejectedException exception) {
            synchronized (connection) { connection.scheduled.set(false); }
            close(connection, exception);
        }
    }

    private void drainOnce(Connection connection) {
        AdenOperatorQueryService.EventBatch batch = connection.takeInitial();
        if (batch == null) {
            batch = queries.events(connection.principal, connection.workspaceId,
                    connection.cursor, replayBatchSize, connection.correlationId);
        }
        boolean sent = false;
        while (batch != null) {
            for (AdenOperatorQueryService.EventEnvelope event : batch.items()) {
                String nextCursor = queries.streamCursor(connection.workspaceId,
                        Long.parseLong(event.sequence()));
                // SSE 的线级事件名固定；领域事件类型由 envelope.eventType 表达。
                sendWithTimeout(connection, SseEmitter.event().id(nextCursor)
                        .name(SSE_EVENT_NAME).data(event));
                connection.cursor = nextCursor;
                sent = true;
            }
            if (batch.items().size() < replayBatchSize) break;
            batch = queries.events(connection.principal, connection.workspaceId,
                    connection.cursor, replayBatchSize, connection.correlationId);
        }
        if (!sent) {
            // 无事件时仍通过查询服务复核权限、membership、cursor 和保留期。
            queries.events(connection.principal, connection.workspaceId,
                    connection.cursor, 1, connection.correlationId);
            sendWithTimeout(connection, SseEmitter.event().comment("heartbeat"));
        }
    }

    private void sendWithTimeout(Connection connection, SseEmitter.SseEventBuilder event) {
        final Future<?> future;
        try {
            future = sendExecutor.submit(() -> {
                connection.emitter.send(event);
                return null;
            });
        } catch (TaskRejectedException exception) {
            throw new AdenApplicationException("ADEN_STREAM_UNAVAILABLE", "SSE 发送队列已满");
        }
        try {
            future.get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AdenApplicationException("ADEN_STREAM_UNAVAILABLE", "SSE 发送超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AdenApplicationException("ADEN_STREAM_UNAVAILABLE", "SSE 发送被中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof IOException) {
                throw new AdenApplicationException("ADEN_STREAM_UNAVAILABLE", "SSE 客户端连接已断开");
            }
            throw new AdenApplicationException("ADEN_STREAM_UNAVAILABLE", "SSE 发送失败");
        }
    }

    private void close(Connection connection, Throwable failure) {
        if (!connection.closed.compareAndSet(false, true)) return;
        connections.remove(connection.id, connection);
        connection.emitter.completeWithError(failure);
    }

    private void cleanup(Connection connection) {
        connection.closed.set(true);
        connections.remove(connection.id, connection);
    }

    private static final class Connection {
        private final String id;
        private final AdenOperatorPrincipal principal;
        private final AdenWorkspaceId workspaceId;
        private final String correlationId;
        private final SseEmitter emitter;
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private int pendingSignals;
        private volatile String cursor;
        private volatile AdenOperatorQueryService.EventBatch initial;

        private Connection(String id, AdenOperatorPrincipal principal, AdenWorkspaceId workspaceId,
                           String correlationId, SseEmitter emitter, String cursor,
                           AdenOperatorQueryService.EventBatch initial) {
            this.id=id; this.principal=principal; this.workspaceId=workspaceId;
            this.correlationId=correlationId; this.emitter=emitter;
            this.cursor=cursor; this.initial=initial;
        }
        private AdenOperatorQueryService.EventBatch takeInitial() {
            AdenOperatorQueryService.EventBatch value=initial;
            initial=null;
            return value;
        }
    }

    @FunctionalInterface
    interface EmitterFactory {
        SseEmitter create(long timeoutMillis);
    }
}
