package com.ruoyi.fashion.configuration.telemetry;

import io.opentelemetry.context.Context;

import java.util.Objects;
import java.util.Optional;

/** 从内部请求提取出的 OTel Context 与独立业务关联 ID。 */
public record FashionTraceContext(Context context, String correlationId) {

    public FashionTraceContext {
        Objects.requireNonNull(context, "context");
    }

    public Optional<String> optionalCorrelationId() {
        return Optional.ofNullable(correlationId);
    }
}
