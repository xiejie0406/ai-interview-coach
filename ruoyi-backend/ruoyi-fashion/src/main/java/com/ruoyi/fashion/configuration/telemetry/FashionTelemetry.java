package com.ruoyi.fashion.configuration.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Java/Python 内部调用的 OTel API 基座。没有 SDK/Exporter 时为 no-op，不能据此声称采集端已上线。
 */
public final class FashionTelemetry {

    private static final String INSTRUMENTATION_SCOPE = "com.ruoyi.fashion";
    private static final W3CTraceContextPropagator W3C = W3CTraceContextPropagator.getInstance();
    private static final Set<String> IMAGE_SOURCES = Set.of("sample", "upload", "provider", "composition", "reuse");
    private static final Set<String> IMAGE_OUTCOMES = Set.of("created", "queued", "running", "unknown", "success",
            "partial", "failed", "cancel-requested", "cancelled", "review-pass", "review-reject", "adopted");
    private static final Set<String> DELIVERY_TYPES = Set.of("pptx", "csv", "image_zip", "jpg");
    private static final Set<String> DELIVERY_OUTCOMES = Set.of("queued", "running", "success", "failed", "downloaded");

    private final Tracer tracer;
    private final LongCounter internalRequestCounter;
    private final DoubleHistogram internalRequestDuration;
    private final LongCounter agentRunEventCounter;
    private final LongCounter imageTaskEventCounter;
    private final DoubleHistogram imageTaskDuration;
    private final DoubleHistogram imageCost;
    private final LongCounter deliveryEventCounter;
    private final DoubleHistogram deliveryDuration;

    public FashionTelemetry(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry");
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        this.internalRequestCounter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .counterBuilder("fashion.internal.request.count")
                .setDescription("Java/Python internal requests")
                .build();
        this.internalRequestDuration = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .histogramBuilder("fashion.internal.request.duration")
                .setDescription("Java/Python internal request duration")
                .setUnit("ms")
                .build();
        this.agentRunEventCounter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .counterBuilder("fashion.agent.run.event.count")
                .setDescription("Agent run lifecycle events")
                .build();
        this.imageTaskEventCounter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .counterBuilder("fashion.image.task.event.count")
                .setDescription("Quote image task lifecycle events")
                .build();
        this.imageTaskDuration = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .histogramBuilder("fashion.image.task.duration")
                .setDescription("Terminal quote image task duration")
                .setUnit("ms")
                .build();
        this.imageCost = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .histogramBuilder("fashion.image.cost")
                .setDescription("Settled quote image cost in CNY")
                .setUnit("CNY")
                .build();
        this.deliveryEventCounter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .counterBuilder("fashion.delivery.event.count")
                .setDescription("Quote delivery lifecycle events")
                .build();
        this.deliveryDuration = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .histogramBuilder("fashion.delivery.duration")
                .setDescription("Quote delivery generation duration")
                .setUnit("ms")
                .build();
    }

    public FashionTraceContext extract(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers");
        Context context = W3C.extract(Context.root(), headers, CaseInsensitiveMapGetter.INSTANCE);
        String correlationId = validOrGeneratedCorrelationId(
                header(headers, FashionTelemetryHeaders.CORRELATION_ID));
        return new FashionTraceContext(context, correlationId);
    }

    public Map<String, String> inject(Context context, String correlationId) {
        Objects.requireNonNull(context, "context");
        Map<String, String> headers = new LinkedHashMap<>();
        W3C.inject(context, headers, MapSetter.INSTANCE);
        headers.put(
                FashionTelemetryHeaders.CORRELATION_ID,
                validOrGeneratedCorrelationId(correlationId));
        return Map.copyOf(headers);
    }

    public Span startInternalSpan(
            Context parent,
            String spanName,
            FashionSafeLogContext safeContext) {
        return startSpan(parent, spanName, SpanKind.INTERNAL, safeContext);
    }

    public Span startClientSpan(
            Context parent,
            String spanName,
            FashionSafeLogContext safeContext) {
        return startSpan(parent, spanName, SpanKind.CLIENT, safeContext);
    }

    public Span startServerSpan(
            Context parent,
            String spanName,
            FashionSafeLogContext safeContext) {
        return startSpan(parent, spanName, SpanKind.SERVER, safeContext);
    }

    private Span startSpan(
            Context parent,
            String spanName,
            SpanKind spanKind,
            FashionSafeLogContext safeContext) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(spanKind, "spanKind");
        Objects.requireNonNull(safeContext, "safeContext");
        if (spanName == null || !spanName.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("spanName 必须是低基数操作名");
        }
        return tracer.spanBuilder(spanName)
                .setParent(parent)
                .setSpanKind(spanKind)
                .setAllAttributes(safeContext.traceAttributes())
                .startSpan();
    }

    public void recordInternalRequest(Duration duration, FashionSafeLogContext safeContext) {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(safeContext, "safeContext");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration 不能为负数");
        }
        internalRequestCounter.add(1, safeContext.metricAttributes());
        internalRequestDuration.record(duration.toNanos() / 1_000_000.0, safeContext.metricAttributes());
    }

    /** 只接受低基数操作与结果，不允许原始需求、客户信息或异常堆栈进入指标标签。 */
    public void recordAgentRunEvent(String operation, String outcome, long count) {
        if (count < 0) throw new IllegalArgumentException("count 不能为负数");
        if (count == 0) return;
        FashionSafeLogContext context = new FashionSafeLogContext(
                null, null, null, operation, "1.0", "fashion-ai-runtime", outcome);
        agentRunEventCounter.add(count, context.metricAttributes());
    }

    /** 图片指标只使用固定来源与生命周期结果，禁止把 requestKey、方案号或异常正文作为标签。 */
    public void recordImageTaskEvent(String sourceMode, String outcome, long count) {
        if (count < 0) throw new IllegalArgumentException("count 不能为负数");
        if (count == 0) return;
        if (!IMAGE_SOURCES.contains(sourceMode) || !IMAGE_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("图片指标标签不在低基数白名单内");
        }
        FashionSafeLogContext context = new FashionSafeLogContext(
                null, null, null, "image." + sourceMode, "1.0", "fashion-image", outcome);
        imageTaskEventCounter.add(count, context.metricAttributes());
    }

    public void recordImageTaskCompletion(String sourceMode, String outcome, Duration duration, double costCny) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || costCny < 0 || !Double.isFinite(costCny)) {
            throw new IllegalArgumentException("图片耗时和费用必须是有限非负数");
        }
        recordImageTaskEvent(sourceMode, outcome, 1);
        FashionSafeLogContext context = new FashionSafeLogContext(
                null, null, null, "image." + sourceMode, "1.0", "fashion-image", outcome);
        imageTaskDuration.record(duration.toNanos() / 1_000_000.0, context.metricAttributes());
        if (costCny > 0) imageCost.record(costCny, context.metricAttributes());
    }

    /** 文件类型和生命周期均为固定白名单，不允许方案号、客户、文件名或错误正文进入指标标签。 */
    public void recordDeliveryEvent(String fileType, String outcome, long count) {
        if (count < 0) throw new IllegalArgumentException("count 不能为负数");
        if (count == 0) return;
        if (!DELIVERY_TYPES.contains(fileType) || !DELIVERY_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("交付指标标签不在低基数白名单内");
        }
        FashionSafeLogContext context = new FashionSafeLogContext(
                null, null, null, "delivery." + fileType, "1.0", "fashion-delivery", outcome);
        deliveryEventCounter.add(count, context.metricAttributes());
    }

    public void recordDeliveryCompletion(String fileType, String outcome, Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("交付耗时不能为负数");
        recordDeliveryEvent(fileType, outcome, 1);
        FashionSafeLogContext context = new FashionSafeLogContext(
                null, null, null, "delivery." + fileType, "1.0", "fashion-delivery", outcome);
        deliveryDuration.record(duration.toNanos() / 1_000_000.0, context.metricAttributes());
    }

    private static String header(Map<String, String> headers, String expectedName) {
        String normalizedExpected = expectedName.toLowerCase(Locale.ROOT);
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().toLowerCase(Locale.ROOT).equals(normalizedExpected))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String validOrGeneratedCorrelationId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private enum MapSetter implements TextMapSetter<Map<String, String>> {
        INSTANCE;

        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            carrier.put(key, value);
        }
    }

    private enum CaseInsensitiveMapGetter implements TextMapGetter<Map<String, String>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return header(carrier, key);
        }
    }
}
