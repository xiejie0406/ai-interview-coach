package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.application.agent.port.ChatModelRequest;
import com.ruoyi.interview.application.agent.port.ChatModelResult;
import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ModelMessage;
import com.ruoyi.interview.application.agent.port.ModelUsage;
import com.ruoyi.interview.application.agent.port.ProviderFailure;
import com.ruoyi.interview.domain.platform.RetryDisposition;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DeepSeek Chat Completions HTTP adapter.
 *
 * <p>该边界只接受 Provider-neutral DTO，不记录 Key、Prompt、响应正文或供应商原始错误。</p>
 */
public final class DeepSeekChatModelAdapter implements ChatModelPort, ProviderAdapter {
    public static final String ADAPTER_ID = "deepseek-chat";
    public static final String NOT_CONFIGURED = "CAPABILITY_NOT_CONFIGURED";

    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final int MAX_RESPONSE_BYTES = 2_097_152;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final URI endpoint;
    private final Map<String, String> approvedModels;
    private final String thinkingMode;

    public DeepSeekChatModelAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String interviewModel,
            String evaluationModel,
            String thinkingMode
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.apiKey = normalized(apiKey);
        this.endpoint = chatCompletionsEndpoint(baseUrl);
        this.approvedModels = approvedModels(interviewModel, evaluationModel);
        this.thinkingMode = approvedThinkingMode(thinkingMode);
    }

    @Override
    public ChatModelResult execute(ChatModelRequest request, InvocationContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        if (!available()) {
            return failure(NOT_CONFIGURED, RetryDisposition.NOT_RETRYABLE, Optional.empty(), Optional.empty());
        }

        final byte[] requestBody;
        try {
            requestBody = encodeRequest(request);
        } catch (JacksonException | IllegalArgumentException exception) {
            return failure("PROVIDER_REQUEST_INVALID", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        }
        if (requestBody.length > MAX_REQUEST_BYTES) {
            return failure("PROVIDER_REQUEST_TOO_LARGE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        }

        final HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(context.timeBudget().duration())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
        } catch (IllegalArgumentException exception) {
            return failure("PROVIDER_CONFIGURATION_INVALID", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        }

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            Optional<String> requestIdHash = providerRequestIdHash(response);
            Optional<Duration> retryAfter = retryAfter(response);
            byte[] responseBody;
            try (InputStream body = response.body()) {
                responseBody = readLimited(body, MAX_RESPONSE_BYTES);
            }
            if (responseBody == null) {
                return failure("PROVIDER_RESPONSE_TOO_LARGE", RetryDisposition.NOT_RETRYABLE,
                        Optional.empty(), requestIdHash);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return httpFailure(response.statusCode(), retryAfter, requestIdHash);
            }
            return decodeSuccess(responseBody, requestIdHash);
        } catch (java.net.http.HttpTimeoutException exception) {
            return failure("PROVIDER_TIMEOUT", RetryDisposition.SAFE_BACKOFF,
                    Optional.empty(), Optional.empty());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure("PROVIDER_CALL_INTERRUPTED", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        } catch (IOException exception) {
            return failure("PROVIDER_UNAVAILABLE", RetryDisposition.SAFE_BACKOFF,
                    Optional.empty(), Optional.empty());
        } catch (RuntimeException exception) {
            return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), Optional.empty());
        }
    }

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean available() {
        return apiKey != null && endpoint != null && !approvedModels.isEmpty() && thinkingMode != null;
    }

    @Override
    public String reasonCode() {
        return available() ? "AVAILABLE" : NOT_CONFIGURED;
    }

    private byte[] encodeRequest(ChatModelRequest request) throws JacksonException {
        List<Map<String, String>> messages = request.messages().stream()
                .map(message -> Map.of(
                        "role", providerRole(message.role()),
                        "content", message.content()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveApprovedModel(request));
        payload.put("messages", messages);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("thinking", Map.of("type", thinkingMode));
        payload.put("stream", false);
        applyApprovedParameters(payload, request.parameters());
        return objectMapper.writeValueAsBytes(payload);
    }

    private String resolveApprovedModel(ChatModelRequest request) {
        String modelAlias = request.providerConfigRef().modelAlias();
        String model = approvedModels.get(modelAlias);
        if (model == null) {
            throw new IllegalArgumentException("model alias is not in the approved DeepSeek allowlist");
        }
        return model;
    }

    private static Map<String, String> approvedModels(String interviewModel, String evaluationModel) {
        Map<String, String> models = new LinkedHashMap<>();
        addApprovedModel(models, interviewModel);
        addApprovedModel(models, evaluationModel);
        return Map.copyOf(models);
    }

    private static void addApprovedModel(Map<String, String> models, String configuredModel) {
        String model = normalized(configuredModel);
        if (model != null) {
            models.put(model, model);
        }
    }

    private static String approvedThinkingMode(String value) {
        String mode = normalized(value);
        return "disabled".equals(mode) || "enabled".equals(mode) ? mode : null;
    }

    private static void applyApprovedParameters(Map<String, Object> payload, Map<String, String> parameters) {
        if (parameters.containsKey("temperature")) {
            BigDecimal temperature = boundedDecimal(parameters.get("temperature"), "temperature",
                    BigDecimal.ZERO, new BigDecimal("2"));
            payload.put("temperature", temperature);
        }
        if (parameters.containsKey("topP")) {
            BigDecimal topP = boundedDecimal(parameters.get("topP"), "topP",
                    BigDecimal.ZERO, BigDecimal.ONE);
            payload.put("top_p", topP);
        }
        if (parameters.containsKey("maxTokens")) {
            int maxTokens = Integer.parseInt(parameters.get("maxTokens"));
            if (maxTokens <= 0 || maxTokens > 65_536) {
                throw new IllegalArgumentException("maxTokens is outside the approved range");
            }
            payload.put("max_tokens", maxTokens);
        }
    }

    private ChatModelResult decodeSuccess(byte[] responseBody, Optional<String> requestIdHash) {
        try {
            Map<?, ?> envelope = objectMapper.readValue(responseBody, Map.class);
            Map<?, ?> choice = firstObject(envelope.get("choices"));
            Map<?, ?> message = object(choice.get("message"));
            Object contentValue = message.get("content");
            if (!(contentValue instanceof String content) || content.isBlank()) {
                return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE,
                        Optional.empty(), requestIdHash);
            }
            Map<?, ?> decoded = objectMapper.readValue(content, Map.class);
            Map<String, Object> structuredOutput = stringKeyedObject(decoded);
            ModelUsage usage = usage(envelope.get("usage"));
            return new ChatModelResult.Success(structuredOutput, usage, requestIdHash);
        } catch (JacksonException | IllegalArgumentException exception) {
            return failure("PROVIDER_BAD_RESPONSE", RetryDisposition.NOT_RETRYABLE,
                    Optional.empty(), requestIdHash);
        }
    }

    private static ModelUsage usage(Object value) {
        if (!(value instanceof Map<?, ?> rawUsage)) {
            return new ModelUsage(List.of());
        }
        List<UsageQuantity> quantities = new ArrayList<>();
        addUsage(quantities, rawUsage.get("prompt_tokens"), "input-token");
        addUsage(quantities, rawUsage.get("completion_tokens"), "output-token");
        return new ModelUsage(quantities);
    }

    private static void addUsage(List<UsageQuantity> quantities, Object value, String unit) {
        if (value instanceof Number number) {
            BigDecimal quantity = new BigDecimal(number.toString());
            if (quantity.signum() >= 0 && quantity.scale() <= 8) {
                quantities.add(new UsageQuantity(unit, quantity));
            }
        }
    }

    private static ChatModelResult httpFailure(
            int status,
            Optional<Duration> retryAfter,
            Optional<String> requestIdHash
    ) {
        if (status == 401 || status == 403) {
            return failure("PROVIDER_AUTHENTICATION_FAILED", RetryDisposition.REQUIRES_HUMAN,
                    Optional.empty(), requestIdHash);
        }
        if (status == 402) {
            return failure("PROVIDER_QUOTA_EXHAUSTED", RetryDisposition.REQUIRES_HUMAN,
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

    private static ChatModelResult failure(
            String errorClass,
            RetryDisposition retryDisposition,
            Optional<Duration> retryAfter,
            Optional<String> requestIdHash
    ) {
        return new ChatModelResult.Failure(new ProviderFailure(
                errorClass, retryDisposition, retryAfter, requestIdHash));
    }

    private static String providerRole(ModelMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private static Map<?, ?> firstObject(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("choices must contain an object");
        }
        return object(list.get(0));
    }

    private static Map<?, ?> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("value must be an object");
        }
        return map;
    }

    private static Map<String, Object> stringKeyedObject(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String textKey) || textKey.isBlank()) {
                throw new IllegalArgumentException("structured output keys must be non-blank strings");
            }
            result.put(textKey, value);
        });
        return result;
    }

    private static byte[] readLimited(InputStream body, int maximumBytes) throws IOException {
        byte[] bytes = body.readNBytes(maximumBytes + 1);
        return bytes.length > maximumBytes ? null : bytes;
    }

    private static Optional<String> providerRequestIdHash(HttpResponse<?> response) {
        return List.of("x-request-id", "x-ds-trace-id", "request-id").stream()
                .map(header -> response.headers().firstValue(header))
                .flatMap(Optional::stream)
                .filter(value -> !value.isBlank())
                .findFirst()
                .map(DeepSeekChatModelAdapter::sha256);
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

    private static BigDecimal boundedDecimal(String value, String name, BigDecimal minimum, BigDecimal maximum) {
        BigDecimal parsed = new BigDecimal(value);
        if (parsed.compareTo(minimum) < 0 || parsed.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " is outside the approved range");
        }
        return parsed;
    }

    private static URI chatCompletionsEndpoint(String baseUrl) {
        String normalized = normalized(baseUrl);
        if (normalized == null) {
            return null;
        }
        try {
            URI base = URI.create(normalized);
            if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null) {
                return null;
            }
            String path = normalized.replaceAll("/+$", "");
            if (path.endsWith("/chat/completions")) {
                return URI.create(path);
            }
            return URI.create(path + "/chat/completions");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("required SHA-256 digest is unavailable", exception);
        }
    }
}

