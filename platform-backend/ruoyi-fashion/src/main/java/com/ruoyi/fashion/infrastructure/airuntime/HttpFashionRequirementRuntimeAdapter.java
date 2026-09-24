package com.ruoyi.fashion.infrastructure.airuntime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.fashion.application.agent.run.FashionRuntimeException;
import com.ruoyi.fashion.application.agent.run.RequirementRuntimeResult;
import com.ruoyi.fashion.application.agent.run.RunWorkItem;
import com.ruoyi.fashion.application.agent.run.port.FashionRequirementRuntimePort;
import com.ruoyi.fashion.configuration.FashionAiRuntimeProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionSafeLogContext;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetryHeaders;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceAuthHeaders;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentity;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/** Java 到 Python 的窄适配器；只传稳定引用和已允许的需求字段。 */
public final class HttpFashionRequirementRuntimeAdapter implements FashionRequirementRuntimePort {
    private static final String PATH = "/internal/v1/requirement-analysis";
    private static final String PRODUCT_PATH = "/internal/v1/product-attribute-suggestion";
    private static final String SELECTION_PATH = "/internal/v1/selection-styling";

    private final FashionAiRuntimeProperties properties;
    private final FashionServiceIdentity identity;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final FashionTelemetry telemetry;

    public HttpFashionRequirementRuntimeAdapter(
            FashionAiRuntimeProperties properties,
            FashionServiceIdentity identity,
            ObjectMapper objectMapper,
            FashionTelemetry telemetry) {
        this.properties = properties;
        this.identity = identity;
        this.objectMapper = objectMapper;
        this.telemetry = telemetry;
        /*
         * Uvicorn does not support clear-text HTTP/2 upgrade. The JDK client otherwise sends h2c upgrade headers;
         * for a POST Uvicorn can reject the upgrade before consuming the body, so the signed digest is checked
         * against an empty body and every authenticated business request becomes 401. Internal TLS may still be
         * terminated upstream, but this hop deliberately uses deterministic HTTP/1.1 framing.
         */
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public boolean available() {
        return identity.configured() && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    @Override
    public RequirementRuntimeResult analyze(RunWorkItem item) {
        return call(item, PATH, requestBody(item, UUID.randomUUID().toString()));
    }

    @Override
    public RequirementRuntimeResult suggestProductAttributes(RunWorkItem item) {
        return call(item, PRODUCT_PATH, productRequestBody(item, UUID.randomUUID().toString()));
    }

    @Override
    public RequirementRuntimeResult rankSelection(RunWorkItem item) {
        return call(item, SELECTION_PATH, selectionRequestBody(item, UUID.randomUUID().toString()));
    }

    private RequirementRuntimeResult call(RunWorkItem item, String path, ObjectNode body) {
        if (!available()) {
            throw new FashionRuntimeException("PROVIDER_DISABLED", "AI Runtime 或服务身份未启用", false);
        }
        String requestId = body.path("request_id").asText();
        String operation = SELECTION_PATH.equals(path) ? "selection-styling"
                : PRODUCT_PATH.equals(path) ? "product-attribute-suggestion" : "requirement-analysis";
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception exception) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "Runtime 请求编码失败", false);
        }
        FashionServiceAuthHeaders auth = identity.sign(new FashionServiceRequest("POST", path, bytes));
        Duration remaining = Duration.between(Instant.now(), item.deadlineAt());
        if (remaining.isZero() || remaining.isNegative()) remaining = Duration.ofMillis(1);
        FashionSafeLogContext safeContext = new FashionSafeLogContext(
                item.runNo(), requestId, Long.toString(item.runId()), operation,
                "1.0", "fashion-ai-runtime", null);
        Span span = telemetry.startClientSpan(Context.current(), "fashion.runtime.call", safeContext);
        Map<String, String> traceHeaders = telemetry.inject(Context.current().with(span), item.runNo());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl().replaceAll("/+$", "") + path))
                .timeout(remaining)
                .header("Content-Type", "application/json")
                .header("X-Request-Id", requestId)
                .header(FashionTelemetryHeaders.CORRELATION_ID,
                        traceHeaders.get(FashionTelemetryHeaders.CORRELATION_ID))
                .header(FashionTelemetryHeaders.TRACEPARENT,
                        traceHeaders.getOrDefault(FashionTelemetryHeaders.TRACEPARENT, traceparent()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        String tracestate = traceHeaders.get(FashionTelemetryHeaders.TRACESTATE);
        if (tracestate != null) builder.header(FashionTelemetryHeaders.TRACESTATE, tracestate);
        auth.toMap().forEach(builder::header);
        Instant startedAt = Instant.now();
        try (Scope ignored = span.makeCurrent()) {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() == 200) {
                JsonNode result = root.path("result");
                if (!result.isObject()) {
                    throw new FashionRuntimeException("DEPENDENCY_UNAVAILABLE", "Runtime 返回缺少 result", false);
                }
                span.setStatus(StatusCode.OK);
                return new RequirementRuntimeResult(result);
            }
            String code = root.path("error").path("code").asText("DEPENDENCY_UNAVAILABLE");
            String message = root.path("error").path("message").asText("Runtime 请求失败");
            boolean retryable = root.path("error").path("retryable").asBoolean(false);
            throw new FashionRuntimeException(code, message, retryable);
        } catch (FashionRuntimeException exception) {
            span.setStatus(StatusCode.ERROR, exception.code());
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            span.setStatus(StatusCode.ERROR, "UPSTREAM_TIMEOUT");
            throw new FashionRuntimeException("UPSTREAM_TIMEOUT", "Runtime 请求超时", true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            span.setStatus(StatusCode.ERROR, "DEPENDENCY_UNAVAILABLE");
            throw new FashionRuntimeException("DEPENDENCY_UNAVAILABLE", "Runtime 调用被中断", true);
        } catch (Exception exception) {
            span.setStatus(StatusCode.ERROR, "DEPENDENCY_UNAVAILABLE");
            throw new FashionRuntimeException("DEPENDENCY_UNAVAILABLE", "Runtime 暂不可用", true);
        } finally {
            telemetry.recordInternalRequest(Duration.between(startedAt, Instant.now()), safeContext);
            span.end();
        }
    }

    private ObjectNode productRequestBody(RunWorkItem item, String requestId) {
        if (item.productId() == null || !item.productSnapshot().isObject()) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "商品属性任务缺少冻结商品快照", false);
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", "1.0");
        root.put("request_id", requestId);
        root.put("correlation_id", item.runNo());
        root.put("run_id", item.runNo());
        root.put("idempotency_key", item.requestKey());
        root.put("deadline_at", item.deadlineAt().toString());
        ObjectNode input = root.putObject("input");
        input.put("product_ref", Long.toString(item.productId()));
        input.put("product_row_version", item.productRowVersion());
        input.set("current_attributes", item.productSnapshot().deepCopy());
        return root;
    }

    private ObjectNode selectionRequestBody(RunWorkItem item, String requestId) {
        JsonNode selection = item.contextSnapshot().path("selection_snapshot");
        if (!selection.isObject()) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "选品任务缺少冻结候选快照", false);
        }
        ObjectNode root = envelope(item, requestId);
        ObjectNode input = root.putObject("input");
        copySelectionInput(selection, input);
        return root;
    }

    private ObjectNode envelope(RunWorkItem item, String requestId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", "1.0");
        root.put("request_id", requestId);
        root.put("correlation_id", item.runNo());
        root.put("run_id", item.runNo());
        root.put("idempotency_key", item.requestKey());
        root.put("deadline_at", item.deadlineAt().toString());
        return root;
    }

    private void copySelectionInput(JsonNode source, ObjectNode target) {
        for (String name : new String[] {"quote_ref", "quote_row_version", "selection_mode", "requested_qty",
                "budget_maximum_per_set_minor", "requirements", "tiers", "locks", "candidate_set_hash"}) {
            JsonNode value = source.get(name);
            if (value == null) target.putNull(name); else target.set(name, value.deepCopy());
        }
        ArrayNode candidates = target.putArray("frozen_candidates");
        source.path("frozen_candidates").forEach(candidate -> {
            ObjectNode value = candidates.addObject();
            for (String name : new String[] {"candidate_ref", "category_code", "source_ref", "style_ref",
                    "color_code", "color_name", "product_name", "season", "tags",
                    "conservative_unit_price_minor", "total_available_qty", "visual_hash"}) {
                JsonNode field = candidate.get(name);
                if (field == null) value.putNull(name); else value.set(name, field.deepCopy());
            }
            ArrayNode variants = value.putArray("variants");
            candidate.path("variants").forEach(variant -> {
                ObjectNode variantValue = variants.addObject();
                for (String name : new String[] {"product_ref", "product_row_version", "sku_ref", "size_code",
                        "unit_price_minor", "available_qty"}) {
                    JsonNode field = variant.get(name);
                    if (field == null) variantValue.putNull(name); else variantValue.set(name, field.deepCopy());
                }
            });
        });
    }

    private ObjectNode requestBody(RunWorkItem item, String requestId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", "1.0");
        root.put("request_id", requestId);
        root.put("correlation_id", item.runNo());
        root.put("run_id", item.runNo());
        root.put("idempotency_key", item.requestKey());
        root.put("deadline_at", item.deadlineAt().toString());
        ObjectNode input = root.putObject("input");
        input.put("customer_ref", item.customerCode());
        input.put("source_text", item.sourceText());
        ObjectNode known = input.putObject("known_requirements");
        putText(known, "scheme_name", null);
        copyText(item.knownRequirements(), known, "audience");
        copyText(item.knownRequirements(), known, "scene");
        copyText(item.knownRequirements(), known, "season");
        copyText(item.knownRequirements(), known, "style");
        known.set("preferred_colors", array(item.knownRequirements().path("preferred_colors")));
        known.set("exclusions", array(item.knownRequirements().path("exclusions")));
        known.put("set_count", item.requestedQty());
        copyText(item.knownRequirements(), known, "delivery_date");
        known.putArray("size_requirements");
        known.put("selection_mode", item.progressive() ? "progressive" : "independent");
        ArrayNode tiers = known.putArray("category_tiers");
        item.comboTemplate().path("groups").forEach(group -> {
            ObjectNode tier = tiers.addObject();
            tier.put("category_count", group.path("count").asInt());
            tier.put("candidate_count", group.path("candidate_count").asInt());
            ArrayNode slots = tier.putArray("slots");
            int[] index = {1};
            group.path("slots").forEach(slot -> {
                ObjectNode value = slots.addObject();
                value.put("slot_index", index[0]++);
                value.put("category", slot.asText());
                value.put("required", true);
            });
        });
        ArrayNode budgets = known.putArray("budget_constraints");
        if (item.budget() != null) {
            long minor = item.budget().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            ObjectNode budget = budgets.addObject();
            budget.put("currency", "CNY");
            budget.put("basis", item.budgetBasis());
            budget.put("minimum_minor", 1);
            budget.put("maximum_minor", minor);
            budget.put("includes_fees", true);
        }
        return root;
    }

    private ArrayNode array(JsonNode source) {
        ArrayNode result = objectMapper.createArrayNode();
        if (source.isArray()) source.forEach(node -> result.add(node.asText()));
        return result;
    }

    private static void copyText(JsonNode source, ObjectNode target, String name) {
        JsonNode value = source.path(name);
        putText(target, name, value.isTextual() ? value.asText() : null);
    }

    private static void putText(ObjectNode target, String name, String value) {
        if (value == null || value.isBlank()) target.putNull(name); else target.put(name, value);
    }

    private static String traceparent() {
        byte[] trace = new byte[16];
        byte[] span = new byte[8];
        new java.security.SecureRandom().nextBytes(trace);
        new java.security.SecureRandom().nextBytes(span);
        return "00-" + HexFormat.of().formatHex(trace) + "-" + HexFormat.of().formatHex(span) + "-01";
    }
}
