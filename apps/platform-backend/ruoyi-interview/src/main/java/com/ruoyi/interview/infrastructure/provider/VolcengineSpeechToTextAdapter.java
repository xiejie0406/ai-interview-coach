package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ModelUsage;
import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.domain.platform.RetryDisposition;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 火山豆包流式 ASR 2.0 WebSocket adapter。 */
public final class VolcengineSpeechToTextAdapter implements SpeechToTextPort, ProviderAdapter {
    public static final String ADAPTER_ID = "volcengine-streaming-asr";
    public static final String NOT_CONFIGURED = "ASR_NOT_CONFIGURED";
    public static final URI DEFAULT_ENDPOINT = URI.create(
            "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async");

    private static final int MAX_AUDIO_BYTES = 20 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int AUDIO_CHUNK_BYTES = 6_400;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AudioArtifactSource artifactSource;
    private final String apiKey;
    private final String modelProfile;
    private final String resourceId;
    private final URI endpoint;

    public VolcengineSpeechToTextAdapter(HttpClient httpClient, ObjectMapper objectMapper,
                                         AudioArtifactSource artifactSource, String apiKey,
                                         String modelProfile, URI endpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.artifactSource = Objects.requireNonNull(artifactSource, "artifactSource");
        this.apiKey = VolcengineSpeechProfiles.normalized(apiKey);
        this.modelProfile = VolcengineSpeechProfiles.normalized(modelProfile);
        this.resourceId = VolcengineSpeechProfiles.asrResourceId(modelProfile);
        this.endpoint = validWebSocketEndpoint(endpoint);
    }

    @Override
    public Result transcribe(Request request, InvocationContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        if (!available()) {
            return failure(NOT_CONFIGURED, RetryDisposition.NOT_RETRYABLE, Optional.empty());
        }
        if (!request.providerConfigRef().modelAlias().equals(modelProfile)) {
            return failure("PROVIDER_REQUEST_INVALID", RetryDisposition.NOT_RETRYABLE, Optional.empty());
        }

        final AudioArtifactSource.AudioContent audio;
        try {
            audio = artifactSource.load(context.tenantId(), request.audioArtifact());
        } catch (RuntimeException exception) {
            return failure("ASR_AUDIO_UNAVAILABLE", RetryDisposition.SAFE_BACKOFF, Optional.empty());
        }
        byte[] audioBytes = audio.bytes();
        if (audioBytes.length > MAX_AUDIO_BYTES) {
            return failure("ASR_AUDIO_TOO_LARGE", RetryDisposition.NOT_RETRYABLE, Optional.empty());
        }

        String requestId = UUID.randomUUID().toString();
        Optional<String> requestIdHash = VolcengineSpeechSupport.requestIdHash(requestId);
        AsrListener listener = new AsrListener(objectMapper, request.offsetUnit(), request.language(),
                requestIdHash);
        final WebSocket socket;
        try {
            socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(minimum(context.timeBudget().duration(), Duration.ofSeconds(10)))
                    .header("X-Api-Key", apiKey)
                    .header("X-Api-Resource-Id", resourceId)
                    .header("X-Api-Request-Id", requestId)
                    .header("X-Api-Connect-Id", requestId)
                    .buildAsync(endpoint, listener)
                    .get(context.timeBudget().duration().toMillis(), TimeUnit.MILLISECONDS);
            send(socket, fullRequest(audio, request), context.timeBudget().duration());
            sendAudio(socket, audioBytes, context.timeBudget().duration());
            Result result = listener.result().get(
                    context.timeBudget().duration().toMillis(), TimeUnit.MILLISECONDS);
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "complete");
            return result;
        } catch (TimeoutException exception) {
            return failure("PROVIDER_TIMEOUT", RetryDisposition.SAFE_BACKOFF, requestIdHash);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure("PROVIDER_CALL_INTERRUPTED", RetryDisposition.NOT_RETRYABLE, requestIdHash);
        } catch (ExecutionException exception) {
            return failure("PROVIDER_UNAVAILABLE", RetryDisposition.SAFE_BACKOFF, requestIdHash);
        } catch (JacksonException | IllegalArgumentException exception) {
            return failure("PROVIDER_REQUEST_INVALID", RetryDisposition.NOT_RETRYABLE, requestIdHash);
        } catch (RuntimeException exception) {
            return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE, requestIdHash);
        }
    }

    @Override public String adapterId() { return ADAPTER_ID; }
    @Override public boolean available() {
        return apiKey != null && resourceId != null && endpoint != null;
    }
    @Override public String reasonCode() { return available() ? "AVAILABLE" : NOT_CONFIGURED; }

    private byte[] fullRequest(AudioArtifactSource.AudioContent audio, Request request) throws JacksonException {
        Map<String, Object> audioConfig = new LinkedHashMap<>();
        audioConfig.put("format", audio.format());
        audioConfig.put("codec", audio.codec());
        audioConfig.put("rate", audio.sampleRate());
        audioConfig.put("bits", audio.bitsPerSample());
        audioConfig.put("channel", audio.channels());
        audioConfig.put("language", request.language());

        Map<String, Object> requestConfig = new LinkedHashMap<>();
        requestConfig.put("model_name", "bigmodel");
        requestConfig.put("enable_nonstream", true);
        requestConfig.put("enable_itn", true);
        requestConfig.put("enable_punc", true);
        requestConfig.put("show_utterances", true);
        if (!request.hotwords().isEmpty()) {
            String context = objectMapper.writeValueAsString(Map.of("hotwords",
                    request.hotwords().stream().map(word -> Map.of("word", word)).toList()));
            requestConfig.put("corpus", Map.of("context", context));
        }
        byte[] json = objectMapper.writeValueAsBytes(Map.of(
                "user", Map.of("uid", "tenant-scoped-user"),
                "audio", audioConfig,
                "request", requestConfig));
        return frame(0x10, 0x11, gzip(json));
    }

    private static void sendAudio(WebSocket socket, byte[] bytes, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        for (int offset = 0; offset < bytes.length; offset += AUDIO_CHUNK_BYTES) {
            int end = Math.min(bytes.length, offset + AUDIO_CHUNK_BYTES);
            boolean last = end == bytes.length;
            byte[] payload = gzip(Arrays.copyOfRange(bytes, offset, end));
            send(socket, frame(last ? 0x22 : 0x20, 0x01, payload), timeout);
        }
    }

    private static void send(WebSocket socket, byte[] bytes, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        socket.sendBinary(ByteBuffer.wrap(bytes), true)
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static byte[] frame(int messageTypeAndFlags, int serializationAndCompression, byte[] payload) {
        ByteBuffer frame = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.BIG_ENDIAN);
        frame.put((byte) 0x11);
        frame.put((byte) messageTypeAndFlags);
        frame.put((byte) serializationAndCompression);
        frame.put((byte) 0x00);
        frame.putInt(payload.length);
        frame.put(payload);
        return frame.array();
    }

    private static byte[] gzip(byte[] bytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(bytes);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory gzip failed", exception);
        }
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] decoded = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (decoded.length > MAX_RESPONSE_BYTES) {
                throw new IOException("ASR response exceeds limit");
            }
            return decoded;
        }
    }

    private static Duration minimum(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static URI validWebSocketEndpoint(URI value) {
        if (value == null || !"wss".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getFragment() != null) {
            return null;
        }
        return value;
    }

    private static Result failure(String errorClass, RetryDisposition disposition,
                                  Optional<String> requestIdHash) {
        return new Failure(VolcengineSpeechSupport.failure(errorClass, disposition,
                Optional.empty(), requestIdHash));
    }

    private static final class AsrListener implements WebSocket.Listener {
        private final ObjectMapper mapper;
        private final String offsetUnit;
        private final String language;
        private final Optional<String> requestIdHash;
        private final CompletableFuture<Result> result = new CompletableFuture<>();
        private final ByteArrayOutputStream currentMessage = new ByteArrayOutputStream();

        private AsrListener(ObjectMapper mapper, String offsetUnit, String language,
                            Optional<String> requestIdHash) {
            this.mapper = mapper;
            this.offsetUnit = offsetUnit;
            this.language = language;
            this.requestIdHash = requestIdHash;
        }

        CompletableFuture<Result> result() { return result; }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public synchronized CompletableFuture<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] fragment = new byte[data.remaining()];
            data.get(fragment);
            if (currentMessage.size() + fragment.length > MAX_RESPONSE_BYTES) {
                result.complete(failure("PROVIDER_RESPONSE_TOO_LARGE",
                        RetryDisposition.NOT_RETRYABLE, requestIdHash));
                webSocket.abort();
                return CompletableFuture.completedFuture(null);
            }
            currentMessage.writeBytes(fragment);
            if (last) {
                byte[] message = currentMessage.toByteArray();
                currentMessage.reset();
                parse(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            result.complete(failure("PROVIDER_UNAVAILABLE", RetryDisposition.SAFE_BACKOFF, requestIdHash));
        }

        private void parse(byte[] message) {
            try {
                if (message.length < 8) {
                    throw new IllegalArgumentException("ASR response frame is too short");
                }
                int headerBytes = (message[0] & 0x0f) * 4;
                int messageType = (message[1] >>> 4) & 0x0f;
                int flags = message[1] & 0x0f;
                boolean gzip = (message[2] & 0x0f) == 1;
                ByteBuffer input = ByteBuffer.wrap(message).order(ByteOrder.BIG_ENDIAN);
                input.position(headerBytes);
                if (messageType == 0x0f) {
                    int code = input.getInt();
                    result.complete(failure("ASR_PROVIDER_ERROR_" + code,
                            VolcengineSpeechSupport.dispositionForProviderCode(code), requestIdHash));
                    return;
                }
                if (messageType != 0x09) {
                    throw new IllegalArgumentException("unexpected ASR response type");
                }
                if (flags == 1 || flags == 3) {
                    input.getInt();
                }
                int size = input.getInt();
                if (size < 0 || size > input.remaining()) {
                    throw new IllegalArgumentException("invalid ASR response payload size");
                }
                byte[] payload = new byte[size];
                input.get(payload);
                if (gzip) {
                    payload = gunzip(payload);
                }
                Map<?, ?> envelope = mapper.readValue(payload, Map.class);
                if (flags == 3) {
                    result.complete(success(envelope));
                }
            } catch (Exception exception) {
                result.complete(failure("PROVIDER_BAD_RESPONSE",
                        RetryDisposition.NOT_RETRYABLE, requestIdHash));
            }
        }

        private Result success(Map<?, ?> envelope) {
            Object rawResult = envelope.get("result");
            if (!(rawResult instanceof Map<?, ?> resultObject)) {
                return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE, requestIdHash);
            }
            Object rawText = resultObject.get("text");
            if (!(rawText instanceof String text) || text.isBlank()) {
                return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE, requestIdHash);
            }
            List<UsageQuantity> quantities = durationMillis(envelope)
                    .map(value -> List.of(new UsageQuantity("audio-millisecond", value)))
                    .orElseGet(List::of);
            return new Success(text, language, offsetUnit, List.of(),
                    new ModelUsage(quantities), requestIdHash);
        }

        private static Optional<BigDecimal> durationMillis(Map<?, ?> envelope) {
            Object raw = envelope.get("audio_info");
            if (raw instanceof Map<?, ?> audioInfo && audioInfo.get("duration") instanceof Number number) {
                BigDecimal value = new BigDecimal(number.toString());
                return value.signum() >= 0 ? Optional.of(value) : Optional.empty();
            }
            return Optional.empty();
        }
    }
}

