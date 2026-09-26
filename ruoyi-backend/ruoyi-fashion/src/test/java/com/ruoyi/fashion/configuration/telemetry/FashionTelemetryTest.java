package com.ruoyi.fashion.configuration.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FashionTelemetryTest {

    private final FashionTelemetry telemetry = new FashionTelemetry(OpenTelemetry.noop());

    @Test
    void propagatesW3CTraceContextAndCorrelationIdWithoutBaggage() {
        SpanContext spanContext = SpanContext.create(
                "0af7651916cd43dd8448eb211c80319c",
                "b7ad6b7169203331",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        Context source = Context.root().with(Span.wrap(spanContext));

        Map<String, String> headers = telemetry.inject(source, "corr-20260912-001");
        FashionTraceContext extracted = telemetry.extract(headers);

        assertThat(headers)
                .containsEntry(
                        FashionTelemetryHeaders.TRACEPARENT,
                        "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
                .containsEntry(FashionTelemetryHeaders.CORRELATION_ID, "corr-20260912-001")
                .doesNotContainKey("baggage");
        SpanContext extractedSpanContext = Span.fromContext(extracted.context()).getSpanContext();
        assertThat(extractedSpanContext.getTraceId()).isEqualTo(spanContext.getTraceId());
        assertThat(extractedSpanContext.getSpanId()).isEqualTo(spanContext.getSpanId());
        assertThat(extractedSpanContext.getTraceFlags()).isEqualTo(spanContext.getTraceFlags());
        assertThat(extractedSpanContext.isRemote()).isTrue();
        assertThat(extracted.correlationId()).isEqualTo("corr-20260912-001");
    }

    @Test
    void extractsHeadersCaseInsensitivelyAndIgnoresInvalidTraceparent() {
        FashionTraceContext extracted = telemetry.extract(Map.of(
                "TraceParent", "not-a-traceparent",
                "x-correlation-id", "corr-001"));

        assertThat(Span.fromContext(extracted.context()).getSpanContext().isValid()).isFalse();
        assertThat(extracted.correlationId()).isEqualTo("corr-001");
    }

    @Test
    void replacesMissingOrUntrustedCorrelationIdsInsteadOfThrowing() {
        FashionTraceContext missing = telemetry.extract(Map.of());
        FashionTraceContext invalid = telemetry.extract(Map.of(
                FashionTelemetryHeaders.CORRELATION_ID,
                "corr-001\nAuthorization: Bearer secret"));
        Map<String, String> injected = telemetry.inject(Context.root(), "  ");

        assertThat(missing.correlationId()).matches("[0-9a-f-]{36}");
        assertThat(invalid.correlationId()).matches("[0-9a-f-]{36}");
        assertThat(injected.get(FashionTelemetryHeaders.CORRELATION_ID)).matches("[0-9a-f-]{36}");
    }

    @Test
    void safeLogContextExposesOnlyAllowlistedIdentifiers() {
        FashionSafeLogContext context = new FashionSafeLogContext(
                "corr-001", "request-001", "run-001", "agent.execute", "1", "fashion-ai-runtime", "success");

        assertThat(context.structuredFields())
                .containsOnlyKeys(
                        "correlation_id",
                        "request_id",
                        "run_id",
                        "operation",
                        "contract_version",
                        "peer_service_id",
                        "outcome")
                .doesNotContainKeys("authorization", "signature", "secret", "body");
        telemetry.recordInternalRequest(Duration.ofMillis(15), context);
        Span span = telemetry.startInternalSpan(Context.root(), "fashion.agent.execute", context);
        span.end();
        telemetry.startClientSpan(Context.root(), "fashion.runtime.call", context).end();
        telemetry.startServerSpan(Context.root(), "fashion.gateway.handle", context).end();
    }

    @Test
    void rejectsUnsafeOrHighCardinalityLogValues() {
        assertThatThrownBy(() -> new FashionSafeLogContext(
                "corr-001\nAuthorization: Bearer secret",
                null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> telemetry.recordInternalRequest(
                Duration.ofMillis(-1),
                new FashionSafeLogContext(null, null, null, "agent.execute", null, null, "failure")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void imageLifecycleMetricsAcceptOnlySafeLowCardinalityLabels() {
        telemetry.recordImageTaskEvent("sample", "created", 1);
        telemetry.recordImageTaskEvent("provider", "partial", 0);
        telemetry.recordImageTaskCompletion("provider", "partial", Duration.ofSeconds(2), 1.5d);

        assertThatThrownBy(() -> telemetry.recordImageTaskEvent("provider", "failed", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> telemetry.recordImageTaskEvent("sample/request-123", "created", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> telemetry.recordImageTaskCompletion(
                "provider", "success", Duration.ofMillis(-1), 1d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
