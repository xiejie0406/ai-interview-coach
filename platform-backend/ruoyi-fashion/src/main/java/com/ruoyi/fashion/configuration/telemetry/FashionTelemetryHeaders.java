package com.ruoyi.fashion.configuration.telemetry;

/** 内部调用允许传播的 W3C Trace Context 与业务关联头。 */
public final class FashionTelemetryHeaders {

    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String CORRELATION_ID = "X-Correlation-ID";

    private FashionTelemetryHeaders() {
    }
}
