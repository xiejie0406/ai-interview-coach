package com.ruoyi.interview.controller.websocket;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
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
    private static final long MAX_TOTAL_BYTES = 12L * 1024 * 1024;
    private static final long MAX_TOTAL_DURATION_MILLIS = 120_000L;
    private static final int MAX_TEXT_MESSAGE_BYTES = 2 * 1024 * 1024;
    private static final List<String> CLIENT_CAPABILITIES = List.of(
            "BASE64_AUDIO", "TTS_PLAYBACK", "TRANSCRIPT_REVIEW");
    private final ObjectMapper json;
    private final VoiceSessionTicketPort tickets;
    private final VoiceCaptureCoordinator coordinator;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler(ObjectMapper json, VoiceSessionTicketPort tickets,
                                 VoiceCaptureCoordinator coordinator) {
        this.json = json; this.tickets = tickets; this.coordinator = coordinator;
    }

    @Override
    public List<String> getSubProtocols() {
        return SUB_PROTOCOLS;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            handleTextMessageInternal(session, message);
        } catch (ProtocolViolation violation) {
            close(session, violation.status);
        } catch (AdapterUnavailableException exception) {
            close(session, new CloseStatus(4503, "voice_capability_unavailable"));
        } catch (DomainException exception) {
            if (exception.code() == DomainErrorCode.STALE_TURN
                    || exception.code() == DomainErrorCode.VERSION_CONFLICT) {
                close(session, new CloseStatus(4409, "stale_voice_generation"));
            } else if (exception.code() == DomainErrorCode.INVALID_ARGUMENT) {
                close(session, new CloseStatus(4413, "invalid_voice_payload"));
            } else {
                close(session, new CloseStatus(4408, "voice_protocol_violation"));
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            close(session, new CloseStatus(4413, "invalid_voice_payload"));
        } catch (IllegalStateException exception) {
            close(session, new CloseStatus(4408, "voice_protocol_violation"));
        } catch (RuntimeException exception) {
            close(session, new CloseStatus(4503, "voice_capability_unavailable"));
        }
    }

    private void handleTextMessageInternal(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() <= 0 || message.getPayloadLength() > MAX_TEXT_MESSAGE_BYTES) {
            throw unsupportedPayload();
        }
        final Map<String, Object> envelope;
        try {
            envelope = json.readValue(message.getPayload(), new TypeReference<>() { });
        } catch (JacksonException exception) {
            throw unsupportedPayload();
        }
        validateBaseEnvelope(envelope);
        String type = text(envelope, "type");
        long sequence = number(envelope, "sequence");
        Map<String, Object> data = object(envelope.get("data"));
        validateClientData(type, data);
        CorrelationId correlation = new CorrelationId(text(envelope, "messageId"));
        ResourceId pathSessionId = sessionId(session.getUri());
        Connection connection = connections.get(session.getId());

        if (connection == null) {
            if (!"client.hello".equals(type) || sequence != 1) {
                session.close(new CloseStatus(4401, "hello_required")); return;
            }
            validateHello(data);
            long resumeFromServerSequence = number(data, "resumeFromServerSequence");
            Object principalId = session.getAttributes().get("ruoyiUserId");
            UserId authenticatedUserId = principalId instanceof Number number
                    ? UserId.of(Long.toString(number.longValue())) : null;
            Object tenant = session.getAttributes().get("ruoyiTenantId");
            TenantId authenticatedTenantId = tenant instanceof String value && !value.isBlank()
                    ? TenantId.of(value) : null;
            long requestedGeneration = number(envelope, "generation");
            var ticket = tickets.consume(text(data, "socketTicket"), authenticatedTenantId,
                    pathSessionId, authenticatedUserId, requestedGeneration);
            if (ticket.isEmpty()) { session.close(new CloseStatus(4401, "invalid_ticket")); return; }
            connection = new Connection(ticket.orElseThrow());
            connection.session = session;
            validateEnvelope(envelope, connection);
            coordinator.recordClientSequence(connection.ticket, sequence);
            connection.lastClientSequence = sequence;
            if (resumeFromServerSequence > 0) {
                // 当前实现没有 durable WebSocket replay；旧 cursor 不能被假装接受。
                // 先清理本次 open 已创建的未上传 artifact，再通过 REST snapshot 恢复。
                coordinator.rejectResume(connection.ticket, "VOICE_RESYNC_REQUIRED", correlation);
                connection.completed = true;
                send(session, connection, "server.resync-required", Map.of(
                        "reasonCode", "VOICE_RESYNC_REQUIRED",
                        "snapshotUrl", "/api/v1/interviews/" + pathSessionId.value()));
                session.close(new CloseStatus(4409, "voice_resync_required"));
                return;
            }
            connections.put(session.getId(), connection);
            send(session, connection, "server.hello", Map.of(
                    "protocolVersion", 1, "resumeAccepted", true,
                    "serverSequence", 0, "nextExpectedClientSequence", 2,
                    "flowControl", flowControlLimits()));
            return;
        }

        validateEnvelope(envelope, connection);
        if (sequence <= connection.lastClientSequence) {
            send(session, connection, "server.ack", Map.of("acceptedClientSequence", sequence,
                    "remainingWindow", Math.max(0, MAX_IN_FLIGHT_CHUNKS - connection.inFlightChunks)));
            return;
        }
        if (sequence != connection.lastClientSequence + 1) {
            send(session, connection, "server.nack", Map.of("expectedClientSequence",
                    connection.lastClientSequence + 1, "receivedClientSequence", sequence,
                    "recoverable", true));
            return;
        }
        switch (type) {
            case "client.audio.start" -> {
                if (connection.capture != null) throw new IllegalStateException("audio already started");
                String codec = text(data, "codec");
                if (!codec.equals(connection.ticket.request().codec())) throw unsupportedPayload();
                int sampleRate = intNumber(data, "sampleRate");
                int channelCount = intNumber(data, "channelCount");
                if (sampleRate < 8_000 || sampleRate > 192_000 || channelCount < 1 || channelCount > 8) {
                    throw unsupportedPayload();
                }
                coordinator.recordClientSequence(connection.ticket, sequence);
                connection.capture = coordinator.begin(connection.ticket, sampleRate,
                        channelCount, correlation);
                connection.lastClientSequence = sequence;
                send(session, connection, "voice.turn.state", Map.of("state", "LISTENING",
                        "voiceExecutionVersion", 0));
            }
            case "client.audio.chunk" -> {
                if (connection.capture == null) throw new IllegalStateException("audio not started");
                String encoded = text(data, "bytesBase64");
                if (encoded.length() > ((MAX_CHUNK_BYTES + 2) / 3) * 4) throw unsupportedPayload();
                byte[] bytes = Base64.getDecoder().decode(encoded);
                long duration = number(data, "durationMs");
                if (bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES
                        || duration <= 0 || duration > MAX_BUFFERED_DURATION_MILLIS) {
                    throw unsupportedPayload();
                }
                if (connection.bufferedDuration + duration > MAX_BUFFERED_DURATION_MILLIS
                        || connection.inFlightChunks >= MAX_IN_FLIGHT_CHUNKS) {
                    send(session, connection, "server.flow-control", Map.of(
                            "paused", true, "reasonCode", "VOICE_BACKPRESSURE",
                            "limits", flowControlLimits()));
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
                connection.lastClientSequence = sequence;
                send(session, connection, "server.ack", Map.of("acceptedClientSequence", sequence,
                        "remainingWindow", MAX_IN_FLIGHT_CHUNKS - connection.inFlightChunks));
            }
            case "client.audio.stop" -> {
                if (connection.capture == null) throw new IllegalStateException("audio not started");
                long totalBytes = number(data, "totalBytes");
                long totalDuration = number(data, "totalDurationMs");
                if (totalBytes <= 0 || totalBytes > MAX_TOTAL_BYTES
                        || totalDuration <= 0 || totalDuration > MAX_TOTAL_DURATION_MILLIS) {
                    throw unsupportedPayload();
                }
                coordinator.recordClientSequence(connection.ticket, sequence);
                connection.lastClientSequence = sequence;
                send(session, connection, "voice.turn.state", Map.of("state", "TRANSCRIBING",
                        "voiceExecutionVersion", 0));
                var result = coordinator.complete(connection.capture, totalBytes,
                        totalDuration, correlation);
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
                String reasonCode = reasonCode(data, "reasonCode");
                if (connection.capture != null) coordinator.abort(connection.capture,
                        reasonCode, correlation);
                else coordinator.rejectResume(connection.ticket, reasonCode, correlation);
                connection.lastClientSequence = sequence;
                connection.completed = true;
                send(session, connection, "voice.turn.state", Map.of("state", "CANCELLED",
                        "voiceExecutionVersion", 0));
                session.close(CloseStatus.NORMAL);
            }
            case "client.tts.cancel" -> {
                reasonCode(data, "reasonCode");
                coordinator.recordClientSequence(connection.ticket, sequence);
                connection.lastClientSequence = sequence;
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
        if (connection != null && !connection.completed) {
            try {
                var correlation = new CorrelationId("voice-disconnect-" + session.getId());
                if (connection.capture != null) coordinator.abort(connection.capture, "SOCKET_DISCONNECTED", correlation);
                else coordinator.rejectResume(connection.ticket, "SOCKET_DISCONNECTED", correlation);
            }
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
            if (!request.tenantId().equals(tenantId)
                    || !request.sessionId().equals(sessionId) || !request.turnId().equals(previousTurnId)
                    || connection.ttsStarted) {
                return;
            }
            WebSocketSession socket = connection.session;
            if (socket == null || !socket.isOpen()) return;
            dispatched.set(true);
            connection.ttsStarted = true;
            try {
                send(socket, connection, "tts.state", nullableMap(
                        "state", "STARTED", "outputArtifactId", null,
                        "codec", coordinator.ttsCodec(),
                        "sampleRate", coordinator.ttsSampleRate()));
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
        connection.lastServerSequence = sequence;
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
        if (uri == null) throw unsupportedPayload();
        String[] parts = uri.getPath().split("/");
        if (parts.length != 6 || !"ws".equals(parts[1]) || !"v1".equals(parts[2])
                || !"interviews".equals(parts[3]) || !"voice".equals(parts[5])) {
            throw unsupportedPayload();
        }
        try {
            return ResourceId.of(parts[4]);
        } catch (RuntimeException exception) {
            throw unsupportedPayload();
        }
    }
    private static String text(Map<String, Object> map, String key) {
        if (map == null) throw unsupportedPayload();
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw unsupportedPayload();
        return text.trim();
    }
    private static long number(Map<String, Object> map, String key) {
        if (map == null) throw unsupportedPayload();
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw unsupportedPayload();
        try {
            if (number instanceof java.math.BigDecimal decimal) return decimal.longValueExact();
            if (number instanceof java.math.BigInteger integer) return integer.longValueExact();
            if (number instanceof Double || number instanceof Float) {
                double decimal = number.doubleValue();
                if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                        || decimal < Long.MIN_VALUE || decimal > Long.MAX_VALUE) {
                    throw unsupportedPayload();
                }
            }
            return number.longValue();
        } catch (ArithmeticException exception) {
            throw unsupportedPayload();
        }
    }
    private static int intNumber(Map<String, Object> map, String key) {
        try { return Math.toIntExact(number(map, key)); }
        catch (ArithmeticException exception) { throw unsupportedPayload(); }
    }
    private static void validateEnvelope(Map<String, Object> envelope, Connection connection) {
        var request = connection.ticket.request();
        if (!connection.ticket.voiceSessionId().value().equals(text(envelope, "voiceSessionId"))
                || !request.sessionId().value().equals(text(envelope, "sessionId"))
                || !request.turnId().value().equals(text(envelope, "turnId"))
                || request.socketGeneration() != number(envelope, "generation")
                || number(envelope, "schemaVersion") != 1) {
            throw new ProtocolViolation(new CloseStatus(4409, "stale_voice_generation"));
        }
        if (number(envelope, "ackSequence") > connection.lastServerSequence) {
            throw new ProtocolViolation(new CloseStatus(4408, "voice_protocol_violation"));
        }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) throw unsupportedPayload();
        return (Map<String, Object>) map;
    }
    private static Map<String, Object> nullableMap(String firstKey, Object firstValue,
                                                   String secondKey, Object secondValue) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue); values.put(secondKey, secondValue);
        return values;
    }

    private static Map<String, Object> nullableMap(String firstKey, Object firstValue,
                                                   String secondKey, Object secondValue,
                                                   String thirdKey, Object thirdValue,
                                                   String fourthKey, Object fourthValue) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue); values.put(secondKey, secondValue);
        values.put(thirdKey, thirdValue); values.put(fourthKey, fourthValue);
        return values;
    }
    private static final class Connection {
        private final VoiceSessionTicketPort.ConsumedTicket ticket;
        private long lastClientSequence = 1;
        private long lastServerSequence;
        private VoiceCaptureCoordinator.Capture capture;
        private boolean completed;
        private boolean ttsStarted;
        private volatile boolean ttsCancelled;
        private volatile boolean ttsTerminal;
        private int inFlightChunks;
        private long bufferedDuration;
        private WebSocketSession session;
        private Connection(VoiceSessionTicketPort.ConsumedTicket ticket) { this.ticket = ticket; }
    }

    private static Map<String, Object> flowControlLimits() {
        return Map.of("maxInFlightChunks", MAX_IN_FLIGHT_CHUNKS,
                "maxChunkBytes", MAX_CHUNK_BYTES,
                "maxBufferedDurationMs", MAX_BUFFERED_DURATION_MILLIS);
    }

    private static String reasonCode(Map<String, Object> data, String key) {
        String value = text(data, key);
        if (value.length() > 96 || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*")) {
            throw unsupportedPayload();
        }
        return value;
    }

    private static void validateBaseEnvelope(Map<String, Object> envelope) {
        if (envelope == null || envelope.isEmpty()) throw unsupportedPayload();
        Set<String> allowed = Set.of("messageId", "type", "voiceSessionId", "sessionId", "turnId",
                "generation", "sequence", "ackSequence", "occurredAt", "schemaVersion", "data");
        if (!allowed.containsAll(envelope.keySet())) throw unsupportedPayload();
        String messageId = text(envelope, "messageId");
        try { java.util.UUID.fromString(messageId); }
        catch (IllegalArgumentException exception) { throw unsupportedPayload(); }
        String type = text(envelope, "type");
        if (type.length() > 96 || !type.startsWith("client.")) throw unsupportedPayload();
        text(envelope, "voiceSessionId"); text(envelope, "sessionId"); text(envelope, "turnId");
        long generation = number(envelope, "generation");
        long sequence = number(envelope, "sequence");
        long ackSequence = number(envelope, "ackSequence");
        if (generation <= 0 || sequence <= 0 || ackSequence < 0) throw unsupportedPayload();
        if (number(envelope, "schemaVersion") != 1) throw unsupportedPayload();
        String occurredAt = text(envelope, "occurredAt");
        try { Instant.parse(occurredAt); }
        catch (RuntimeException exception) { throw unsupportedPayload(); }
    }

    private static void validateHello(Map<String, Object> data) {
        Set<String> allowed = Set.of("socketTicket", "protocolVersion", "resumeFromServerSequence", "capabilities");
        if (!allowed.equals(data.keySet())) throw unsupportedPayload();
        if (number(data, "protocolVersion") != 1 || number(data, "resumeFromServerSequence") < 0) {
            throw unsupportedPayload();
        }
        Object capabilities = data.get("capabilities");
        if (!(capabilities instanceof List<?> list) || list.stream().anyMatch(value ->
                !(value instanceof String capability) || !CLIENT_CAPABILITIES.contains(capability)
                        || list.indexOf(value) != list.lastIndexOf(value))) {
            throw unsupportedPayload();
        }
        text(data, "socketTicket");
    }

    private static void validateClientData(String type, Map<String, Object> data) {
        Set<String> allowed;
        switch (type) {
            case "client.hello" -> allowed = Set.of("socketTicket", "protocolVersion",
                    "resumeFromServerSequence", "capabilities");
            case "client.audio.start" -> allowed = Set.of("codec", "sampleRate", "channelCount");
            case "client.audio.chunk" -> allowed = Set.of("bytesBase64", "durationMs");
            case "client.audio.stop" -> allowed = Set.of("totalBytes", "totalDurationMs");
            case "client.audio.cancel", "client.tts.cancel" -> allowed = Set.of("reasonCode");
            default -> {
                return;
            }
        }
        if (!allowed.equals(data.keySet())) throw unsupportedPayload();
    }

    private static ProtocolViolation unsupportedPayload() {
        return new ProtocolViolation(new CloseStatus(4413, "invalid_voice_payload"));
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (Exception ignored) {
            // 传输已断开时不再向日志写入内部异常。
        }
    }

    private static final class ProtocolViolation extends RuntimeException {
        private final CloseStatus status;
        private ProtocolViolation(CloseStatus status) { this.status = status; }
    }
}

