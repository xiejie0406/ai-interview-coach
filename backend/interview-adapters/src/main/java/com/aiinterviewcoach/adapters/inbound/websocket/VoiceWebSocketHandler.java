package com.aiinterviewcoach.adapters.inbound.websocket;

import com.aiinterviewcoach.adapters.outbound.voice.InMemoryVoiceSessionTicketAdapter;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.ResourceId;
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
            var ticket = tickets.consume(String.valueOf(data.get("socketTicket")), pathSessionId);
            if (ticket.isEmpty()) { session.close(new CloseStatus(4401, "invalid_ticket")); return; }
            connection = new Connection(ticket.orElseThrow());
            connections.put(session.getId(), connection);
            coordinator.recordClientSequence(connection.ticket, sequence);
            send(session, connection, "server.hello", Map.of(
                    "protocolVersion", 1, "resumeAccepted", true,
                    "serverSequence", 0, "nextExpectedClientSequence", 2,
                    "flowControl", Map.of("maxInFlightChunks", 8,
                            "maxChunkBytes", 512L * 1024, "maxBufferedDurationMs", 4_000)));
            return;
        }

        if (sequence != connection.lastClientSequence + 1) {
            send(session, connection, "server.nack", Map.of("expectedClientSequence",
                    connection.lastClientSequence + 1, "receivedClientSequence", sequence,
                    "recoverable", false));
            session.close(new CloseStatus(4409, "client_sequence_gap")); return;
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
                coordinator.accept(connection.capture, sequence, bytes, duration, correlation);
                send(session, connection, "server.ack", Map.of("acceptedClientSequence", sequence,
                        "remainingWindow", 8));
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
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(new CloseStatus(4503, "voice_transport_error"));
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
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("missing data"); return (Map<String, Object>) map;
    }
    private static final class Connection {
        private final InMemoryVoiceSessionTicketAdapter.Ticket ticket;
        private long lastClientSequence = 1;
        private VoiceCaptureCoordinator.Capture capture;
        private boolean completed;
        private Connection(InMemoryVoiceSessionTicketAdapter.Ticket ticket) { this.ticket = ticket; }
    }
}
