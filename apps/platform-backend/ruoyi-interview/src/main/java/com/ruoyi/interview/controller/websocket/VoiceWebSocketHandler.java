package com.ruoyi.interview.controller.websocket;

import com.ruoyi.interview.infrastructure.voice.InMemoryVoiceSessionTicketAdapter;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Voice AsyncAPI v1 的文本封装/Base64 音频处理器。 */
public final class VoiceWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private static final List<String> SUB_PROTOCOLS = List.of("aic.voice.v1");
    private static final int MAX_IN_FLIGHT_CHUNKS = 8;
    private static final long MAX_CHUNK_BYTES = 512L * 1024;
    private static final long MAX_BUFFERED_DURATION_MILLIS = 4_000L;
    private final ObjectMapper json;
    private final InMemoryVoiceSessionTicketAdapter tickets;
    private final VoiceCaptureCoordinator coordinator;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler(ObjectMapper json, InMemoryVoiceSessionTicketAdapter tickets,
                                 VoiceCaptureCoordinator coordinator) {
        this.json = json; this.tickets = tickets; this.coordinator = coordinator;
    }

    @Override
    public List<String> getSubProtocols() {
        return SUB_PROTOCOLS;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> envelope = json.readValue(message.getPayload(), new TypeReference<>() { });
        String type = text(envelope, "type");
        long sequence = number(envelope, "sequence");
        Map<String, Object> data = object(envelope.get("data"));
        CorrelationId correlation = new CorrelationId(text(envelope, "messageId"));
        ResourceId pathSessionId = sessionId(session.getUri());
        Connection connection = connections.get(session.getId());

        if (connection == null) {
            if (!"client.hello".equals(type) || sequence != 1) {
                session.close(new CloseStatus(4401, "hello_required")); return;
            }
            Object principalId = session.getAttributes().get("ruoyiUserId");
            UserId authenticatedUserId = principalId instanceof Number number
                    ? UserId.of(Long.toString(number.longValue())) : null;
            var ticket = tickets.consume(String.valueOf(data.get("socketTicket")), pathSessionId,
                    authenticatedUserId);
            if (ticket.isEmpty()) { session.close(new CloseStatus(4401, "invalid_ticket")); return; }
            connection = new Connection(ticket.orElseThrow());
            connection.session = session;
            validateEnvelope(envelope, connection);
            connections.put(session.getId(), connection);
            coordinator.recordClientSequence(connection.ticket, sequence);
            send(session, connection, "server.hello", Map.of(
                    "protocolVersion", 1, "resumeAccepted", true,
                    "serverSequence", 0, "nextExpectedClientSequence", 2,
                    "flowControl", Map.of("maxInFlightChunks", MAX_IN_FLIGHT_CHUNKS,
                            "maxChunkBytes", MAX_CHUNK_BYTES, "maxBufferedDurationMs", MAX_BUFFERED_DURATION_MILLIS)));
            return;
        }

        validateEnvelope(envelope, connection);
        if (sequence <= connection.lastClientSequence) {
            send(session, connection, "server.ack", Map.of("acceptedClientSequence", sequence,
                    "remainingWindow", 8));
            return;
        }
        if (sequence != connection.lastClientSequence + 1) {
            send(session, connection, "server.nack", Map.of("expectedClientSequence",
                    connection.lastClientSequence + 1, "receivedClientSequence", sequence,
                    "recoverable", true));
            return;
        }
        connection.lastClientSequence = sequence;

        switch (type) {
            case "client.audio.start" -> {
                if (connection.capture != null) throw new IllegalStateException("audio already started");
                String codec = String.valueOf(data.get("codec"));
                if (!codec.equals(connection.ticket.request().codec())) throw new IllegalArgumentException("codec mismatch");
                coordinator.recordClientSequence(connection.ticket, sequence);
                connection.capture = coordinator.begin(connection.ticket, intNumber(data, "sampleRate"),
                        intNumber(data, "channelCount"), correlation);
                send(session, connection, "voice.turn.state", Map.of("state", "LISTENING",
                        "voiceExecutionVersion", 0));
            }
            case "client.audio.chunk" -> {
                if (connection.capture == null) throw new IllegalStateException("audio not started");
                byte[] bytes = Base64.getDecoder().decode(String.valueOf(data.get("bytesBase64")));
                long duration = number(data, "durationMs");
                if (bytes.length > MAX_CHUNK_BYTES || duration <= 0 || duration > MAX_BUFFERED_DURATION_MILLIS
                        || connection.bufferedDuration + duration > MAX_BUFFERED_DURATION_MILLIS
                        || connection.inFlightChunks >= MAX_IN_FLIGHT_CHUNKS) {
                    send(session, connection, "server.flow-control", Map.of(
                            "paused", true, "remainingWindow", Math.max(0, MAX_IN_FLIGHT_CHUNKS - connection.inFlightChunks),
                            "reasonCode", "VOICE_BACKPRESSURE"));
                    return;
                }
                connection.inFlightChunks++;
                connection.bufferedDuration += duration;
                try {
                    coordinator.accept(connection.capture, sequence, bytes, duration, correlation);
                } finally {
                    connection.inFlightChunks = Math.max(0, connection.inFlightChunks - 1);
                    connection.bufferedDuration = Math.max(0, connection.bufferedDuration - duration);
                }
                send(session, connection, "server.ack", Map.of("acceptedClientSequence", sequence,
                        "remainingWindow", MAX_IN_FLIGHT_CHUNKS - connection.inFlightChunks));
            }
            case "client.audio.stop" -> {
                if (connection.capture == null) throw new IllegalStateException("audio not started");
                coordinator.recordClientSequence(connection.ticket, sequence);
                send(session, connection, "voice.turn.state", Map.of("state", "TRANSCRIBING",
                        "voiceExecutionVersion", 0));
                var result = coordinator.complete(connection.capture, number(data, "totalBytes"),
                        number(data, "totalDurationMs"), correlation);
                connection.completed = true;
                if (result instanceof VoiceCaptureCoordinator.Success success) {
                    send(session, connection, "asr.final", Map.of(
                            "transcriptId", success.transcriptId().value(),
                            "transcriptVersionId", success.transcriptVersionId().value(),
                            "audioArtifactId", success.artifactId().value(), "text", success.text(),
                            "language", success.language(), "offsetUnit", success.offsetUnit(),
                            "lowConfidenceSpans", success.spans(),
                            "transcriptVersion", success.transcriptVersion()));
                    send(session, connection, "voice.turn.state", Map.of("state", "CONFIRMING",
                            "voiceExecutionVersion", 0));
                } else {
                    String reason = ((VoiceCaptureCoordinator.Failure) result).reasonCode();
                    send(session, connection, "speech.failed", Map.of("reasonCode", reason,
                            "recoverable", true));
                    send(session, connection, "voice.turn.degraded", Map.of("reasonCode", reason,
                            "recoverable", true));
                }
            }
            case "client.audio.cancel" -> {
                coordinator.recordClientSequence(connection.ticket, sequence);
                if (connection.capture != null) coordinator.abort(connection.capture,
                        String.valueOf(data.getOrDefault("reasonCode", "USER_CANCELLED_RECORDING")), correlation);
                connection.completed = true;
                send(session, connection, "voice.turn.state", Map.of("state", "CANCELLED",
                        "voiceExecutionVersion", 0));
                session.close(CloseStatus.NORMAL);
            }
            case "client.tts.cancel" -> {
                coordinator.recordClientSequence(connection.ticket, sequence);
                connection.ttsCancelled = true;
                LinkedHashMap<String, Object> state = new LinkedHashMap<>();
                state.put("state", "CANCELLED");
                state.put("outputArtifactId", null);
                send(session, connection, "tts.state", state);
            }
            default -> {
                send(session, connection, "server.nack", Map.of("expectedClientSequence", sequence,
                        "receivedClientSequence", sequence, "recoverable", false));
                session.close(new CloseStatus(4408, "unsupported_message"));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.remove(session.getId());
        if (connection != null && connection.capture != null && !connection.completed) {
            try { coordinator.abort(connection.capture, "SOCKET_DISCONNECTED",
                    new CorrelationId("voice-disconnect-" + session.getId())); }
            catch (RuntimeException ignored) { /* cleanup failure is persisted by storage/domain where possible */ }
        }
        if (connection != null && connection.ttsStarted && !connection.ttsTerminal) {
            try {
                coordinator.degradeTts(connection.ticket.request().tenantId(), connection.ticket.request().sessionId(),
                        connection.ticket.request().turnId(), "SOCKET_DISCONNECTED");
            } catch (RuntimeException ignored) {
                // 断线清理失败由持久化/恢复扫描处理，不能记录语音正文或凭据。
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(new CloseStatus(4503, "voice_transport_error"));
    }

    public boolean synthesizeNextQuestion(TenantId tenantId, ResourceId sessionId,
                                          ResourceId previousTurnId, String questionText) {
        java.util.concurrent.atomic.AtomicBoolean dispatched = new java.util.concurrent.atomic.AtomicBoolean();
        connections.forEach((socketId, connection) -> {
            var request = connection.ticket.request();
            if (!request.sessionId().equals(sessionId) || !request.turnId().equals(previousTurnId)
                    || connection.ttsStarted) {
                return;
            }
            WebSocketSession socket = connection.session;
            if (socket == null || !socket.isOpen()) return;
            dispatched.set(true);
            connection.ttsStarted = true;
            try {
                send(socket, connection, "tts.state", nullableMap(
                        "state", "STARTED", "outputArtifactId", null));
                var result = coordinator.synthesizeNextQuestion(connection.ticket, questionText,
                        (sequence, bytes, end) -> {
                            try {
                                send(socket, connection, "tts.chunk", Map.of(
                                        "chunkSequence", sequence,
                                        "bytesBase64", Base64.getEncoder().encodeToString(bytes),
                                        "endOfOutput", end));
                            } catch (Exception exception) {
                                throw new IllegalStateException("TTS WebSocket send failed", exception);
                            }
                        }, () -> connection.ttsCancelled || !socket.isOpen(),
                        new CorrelationId("voice-tts-" + socketId));
                if (result instanceof VoiceCaptureCoordinator.TtsSuccess success) {
                    connection.ttsTerminal = true;
                    send(socket, connection, "tts.state", nullableMap(
                            "state", "COMPLETED", "outputArtifactId", success.outputArtifactId().value()));
                } else if (result instanceof VoiceCaptureCoordinator.TtsCancelled) {
                    connection.ttsTerminal = true;
                    send(socket, connection, "tts.state", nullableMap(
                            "state", "CANCELLED", "outputArtifactId", null));
                } else {
                    connection.ttsTerminal = true;
                    String reason = ((VoiceCaptureCoordinator.TtsFailure) result).reasonCode();
                    send(socket, connection, "tts.state", nullableMap(
                            "state", "FAILED", "outputArtifactId", null));
                    send(socket, connection, "voice.turn.degraded",
                            Map.of("reasonCode", reason, "recoverable", true));
                }
            } catch (Exception exception) {
                try {
                    send(socket, connection, "voice.turn.degraded",
                            Map.of("reasonCode", "TTS_PIPELINE_FAILED", "recoverable", true));
                } catch (Exception ignored) {
                    // Transport teardown owns the final close path; no sensitive payload is logged.
                }
            }
        });
        if (!dispatched.get()) {
            coordinator.degradeTts(tenantId, sessionId, previousTurnId, "TTS_SOCKET_NOT_AVAILABLE");
        }
        return dispatched.get();
    }

    public void finishWithoutTts(TenantId tenantId, ResourceId sessionId, ResourceId previousTurnId) {
        coordinator.finishWithoutTts(tenantId, sessionId, previousTurnId);
    }

    private void send(WebSocketSession session, Connection connection, String type,
                      Map<String, Object> data) throws Exception {
        long sequence = coordinator.nextServerSequence(connection.ticket);
        var request = connection.ticket.request();
        LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("messageId", java.util.UUID.randomUUID().toString());
        envelope.put("type", type); envelope.put("voiceSessionId", connection.ticket.voiceSessionId().value());
        envelope.put("sessionId", request.sessionId().value()); envelope.put("turnId", request.turnId().value());
        envelope.put("generation", request.socketGeneration()); envelope.put("sequence", sequence);
        envelope.put("ackSequence", connection.lastClientSequence); envelope.put("occurredAt", Instant.now().toString());
        envelope.put("schemaVersion", 1); envelope.put("data", data);
        synchronized (session) { session.sendMessage(new TextMessage(json.writeValueAsString(envelope))); }
    }

    private static ResourceId sessionId(URI uri) {
        if (uri == null) throw new IllegalArgumentException("missing websocket URI");
        String[] parts = uri.getPath().split("/");
        if (parts.length != 6 || !"ws".equals(parts[1]) || !"v1".equals(parts[2])
                || !"interviews".equals(parts[3]) || !"voice".equals(parts[5])) {
            throw new IllegalArgumentException("invalid voice websocket path");
        }
        return ResourceId.of(parts[4]);
    }
    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key); if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("missing " + key); return text;
    }
    private static long number(Map<String, Object> map, String key) {
        Object value = map.get(key); if (!(value instanceof Number number)) throw new IllegalArgumentException("missing " + key); return number.longValue();
    }
    private static int intNumber(Map<String, Object> map, String key) { return Math.toIntExact(number(map, key)); }
    private static void validateEnvelope(Map<String, Object> envelope, Connection connection) {
        var request = connection.ticket.request();
        if (!connection.ticket.voiceSessionId().value().equals(text(envelope, "voiceSessionId"))
                || !request.sessionId().value().equals(text(envelope, "sessionId"))
                || !request.turnId().value().equals(text(envelope, "turnId"))
                || request.socketGeneration() != number(envelope, "generation")
                || number(envelope, "schemaVersion") != 1) {
            throw new IllegalArgumentException("voice envelope scope or generation mismatch");
        }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("missing data"); return (Map<String, Object>) map;
    }
    private static Map<String, Object> nullableMap(String firstKey, Object firstValue,
                                                   String secondKey, Object secondValue) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue); values.put(secondKey, secondValue);
        return values;
    }
    private static final class Connection {
        private final InMemoryVoiceSessionTicketAdapter.Ticket ticket;
        private long lastClientSequence = 1;
        private VoiceCaptureCoordinator.Capture capture;
        private boolean completed;
        private boolean ttsStarted;
        private volatile boolean ttsCancelled;
        private volatile boolean ttsTerminal;
        private int inFlightChunks;
        private long bufferedDuration;
        private WebSocketSession session;
        private Connection(InMemoryVoiceSessionTicketAdapter.Ticket ticket) { this.ticket = ticket; }
    }
}

