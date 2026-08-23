package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ModelUsage;
import com.ruoyi.interview.application.agent.port.TextToSpeechPort;
import com.ruoyi.interview.domain.platform.RetryDisposition;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 火山豆包 TTS 2.0 单向流式 HTTP adapter。 */
public final class VolcengineTextToSpeechAdapter implements TextToSpeechPort, ProviderAdapter {
    public static final String ADAPTER_ID = "volcengine-streaming-tts";
    public static final String NOT_CONFIGURED = "TTS_NOT_CONFIGURED";
    public static final URI DEFAULT_ENDPOINT = URI.create(
            "https://openspeech.bytedance.com/api/v3/tts/unidirectional");

    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelProfile;
    private final String resourceId;
    private final String defaultVoice;
    private final URI endpoint;
    private final int sampleRate;

    public VolcengineTextToSpeechAdapter(HttpClient httpClient, ObjectMapper objectMapper,
                                         String apiKey, String modelProfile, String defaultVoice,
                                         URI endpoint, int sampleRate) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.apiKey = VolcengineSpeechProfiles.normalized(apiKey);
        this.modelProfile = VolcengineSpeechProfiles.normalized(modelProfile);
        this.resourceId = VolcengineSpeechProfiles.ttsResourceId(modelProfile);
        this.defaultVoice = VolcengineSpeechProfiles.normalized(defaultVoice);
        this.endpoint = validHttpEndpoint(endpoint);
        this.sampleRate = sampleRate;
    }

    @Override
    public Result synthesize(Request request, AudioSink sink, InvocationContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(context, "context");
        if (!request.providerConfigRef().modelAlias().equals(modelProfile)) {
            return failure("PROVIDER_REQUEST_INVALID", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        }
        String voice = configuredVoice(request.voiceProfile());
        if (!available() || voice == null) {
            return failure(NOT_CONFIGURED, RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        }

        String requestId = UUID.randomUUID().toString();
        Optional<String> requestIdHash = VolcengineSpeechSupport.requestIdHash(requestId);
        final byte[] requestBody;
        try {
            requestBody = requestBody(request, voice);
        } catch (JacksonException | IllegalArgumentException exception) {
            return failure("PROVIDER_REQUEST_INVALID", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        }
        if (requestBody.length > MAX_REQUEST_BYTES) {
            return failure("PROVIDER_REQUEST_TOO_LARGE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(context.timeBudget().duration())
                .header("X-Api-Key", apiKey)
                .header("X-Api-Resource-Id", resourceId)
                .header("X-Api-Request-Id", requestId)
                .header("X-Control-Require-Usage-Tokens-Return", "*")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    // 响应正文可能含供应商诊断或用户文本，禁止读入日志/异常。
                }
                return httpFailure(response.statusCode(), retryAfter(response), requestIdHash);
            }
            try (InputStream body = response.body()) {
                return decodeStream(body, sink, requestIdHash);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            return failure("PROVIDER_TIMEOUT", RetryDisposition.SAFE_BACKOFF,
                    Optional.empty(), requestIdHash);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure("PROVIDER_CALL_INTERRUPTED", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        } catch (IOException exception) {
            return failure("PROVIDER_UNAVAILABLE", RetryDisposition.SAFE_BACKOFF,
                    Optional.empty(), requestIdHash);
        } catch (RuntimeException exception) {
            return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        }
    }

    @Override public String adapterId() { return ADAPTER_ID; }
    @Override public boolean available() {
        return apiKey != null && resourceId != null && endpoint != null && sampleRate > 0;
    }
    @Override public String reasonCode() { return available() ? "AVAILABLE" : NOT_CONFIGURED; }

    private byte[] requestBody(Request request, String voice) throws JacksonException {
        Map<String, Object> audioParams = new LinkedHashMap<>();
        audioParams.put("format", providerFormat(request.codec()));
        audioParams.put("sample_rate", sampleRate);
        Map<String, Object> reqParams = new LinkedHashMap<>();
        reqParams.put("text", request.text());
        reqParams.put("speaker", voice);
        reqParams.put("audio_params", audioParams);
        explicitLanguage(request.language()).ifPresent(language -> reqParams.put("explicit_language", language));
        return objectMapper.writeValueAsBytes(Map.of("req_params", reqParams));
    }

    private Result decodeStream(InputStream input, AudioSink sink, Optional<String> requestIdHash)
            throws IOException {
        long sequence = 0;
        long totalBytes = 0;
        long durationMillis = 0;
        BigDecimal billedCharacters = null;
        byte[] pending = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<?, ?> envelope = objectMapper.readValue(line, Map.class);
                int code = integer(envelope.get("code"), -1);
                if (code != 0) {
                    return failure("TTS_PROVIDER_ERROR_" + code,
                            VolcengineSpeechSupport.dispositionForProviderCode(code),
                            Optional.empty(), requestIdHash);
                }
                Object rawData = envelope.get("data");
                if (rawData instanceof String encoded && !encoded.isBlank()) {
                    byte[] current = Base64.getDecoder().decode(encoded);
                    if (current.length == 0 || totalBytes + current.length > MAX_AUDIO_BYTES) {
                        return failure("PROVIDER_RESPONSE_TOO_LARGE", RetryDisposition.NOT_RETRYABLE,
                                Optional.empty(), requestIdHash);
                    }
                    if (pending != null) {
                        sink.accept(++sequence, pending, false);
                    }
                    pending = current;
                    totalBytes += current.length;
                }
                durationMillis = Math.max(durationMillis, durationMillis(envelope));
                billedCharacters = maximum(billedCharacters, billedCharacters(envelope));
            }
        } catch (JacksonException | IllegalArgumentException exception) {
            return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        }
        if (pending == null || totalBytes <= 0 || durationMillis <= 0) {
            return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        }
        sink.accept(++sequence, pending, true);
        List<UsageQuantity> quantities = new ArrayList<>();
        if (billedCharacters != null && billedCharacters.signum() >= 0) {
            quantities.add(new UsageQuantity("text-character", billedCharacters));
        }
        return new Success(totalBytes, durationMillis, new ModelUsage(quantities), requestIdHash);
    }

    private String configuredVoice(String requested) {
        String value = VolcengineSpeechProfiles.normalized(requested);
        if ("default".equalsIgnoreCase(value) || Objects.equals(value, defaultVoice)) {
            return defaultVoice;
        }
        return null;
    }

    private static String providerFormat(String codec) {
        return switch (codec.trim().toLowerCase()) {
            case "mp3", "wav", "pcm", "ogg_opus" -> codec.trim().toLowerCase();
            case "ogg", "opus" -> "ogg_opus";
            default -> throw new IllegalArgumentException("unsupported TTS codec");
        };
    }

    private static Optional<String> explicitLanguage(String language) {
        return switch (language.trim().toLowerCase()) {
            case "zh-cn" -> Optional.of("zh-cn");
            case "en-us", "en" -> Optional.of("en");
            case "ja-jp", "ja" -> Optional.of("ja");
            default -> Optional.empty();
        };
    }

    private static long durationMillis(Map<?, ?> envelope) {
        Object sentence = envelope.get("sentence");
        if (!(sentence instanceof Map<?, ?> sentenceObject)
                || !(sentenceObject.get("words") instanceof List<?> words)) {
            return 0;
        }
        double maximumSeconds = 0;
        for (Object value : words) {
            if (value instanceof Map<?, ?> word && word.get("endTime") instanceof Number number) {
                maximumSeconds = Math.max(maximumSeconds, number.doubleValue());
            }
        }
        return Math.max(0L, Math.round(maximumSeconds * 1_000));
    }

    private static BigDecimal billedCharacters(Map<?, ?> envelope) {
        Object usage = envelope.get("usage");
        if (usage instanceof Map<?, ?> usageObject && usageObject.get("text_words") instanceof Number number) {
            BigDecimal value = new BigDecimal(number.toString());
            return value.signum() >= 0 && value.scale() <= 8 ? value : null;
        }
        return null;
    }

    private static BigDecimal maximum(BigDecimal left, BigDecimal right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.max(right);
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Result httpFailure(int status, Optional<Duration> retryAfter,
                                      Optional<String> requestIdHash) {
        if (status == 401 || status == 403) {
            return failure("PROVIDER_AUTHENTICATION_FAILED", RetryDisposition.REQUIRES_HUMAN,
                    Optional.empty(), requestIdHash);
        }
        if (status == 408) {
            return failure("PROVIDER_TIMEOUT", RetryDisposition.SAFE_BACKOFF, retryAfter, requestIdHash);
        }
        if (status == 429) {
            return failure("PROVIDER_RATE_LIMITED", RetryDisposition.SAFE_BACKOFF, retryAfter, requestIdHash);
        }
        if (status >= 500) {
            return failure("PROVIDER_UNAVAILABLE", RetryDisposition.SAFE_BACKOFF, retryAfter, requestIdHash);
        }
        return failure("PROVIDER_REQUEST_REJECTED", RetryDisposition.NOT_RETRYABLE,
                Optional.empty(), requestIdHash);
    }

    private static Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("retry-after").flatMap(value -> {
            try {
                long seconds = Long.parseLong(value.trim());
                return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        });
    }

    private static Result failure(String errorClass, RetryDisposition disposition,
                                  Optional<Duration> retryAfter, Optional<String> requestIdHash) {
        return new Failure(VolcengineSpeechSupport.failure(
                errorClass, disposition, retryAfter, requestIdHash));
    }

    private static URI validHttpEndpoint(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getFragment() != null) {
            return null;
        }
        return value;
    }
}

