package com.ruoyi.fashion.configuration.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化日志与 trace 的白名单字段；类型本身不接受正文、Authorization、签名或 Secret。
 */
public record FashionSafeLogContext(
        String correlationId,
        String requestId,
        String runId,
        String operation,
        String contractVersion,
        String peerServiceId,
        String outcome) {

    public FashionSafeLogContext {
        correlationId = safeOptional(correlationId, "correlationId");
        requestId = safeOptional(requestId, "requestId");
        runId = safeOptional(runId, "runId");
        operation = safeOptional(operation, "operation");
        contractVersion = safeOptional(contractVersion, "contractVersion");
        peerServiceId = safeOptional(peerServiceId, "peerServiceId");
        outcome = safeOptional(outcome, "outcome");
    }

    public Map<String, String> structuredFields() {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "correlation_id", correlationId);
        putIfPresent(values, "request_id", requestId);
        putIfPresent(values, "run_id", runId);
        putIfPresent(values, "operation", operation);
        putIfPresent(values, "contract_version", contractVersion);
        putIfPresent(values, "peer_service_id", peerServiceId);
        putIfPresent(values, "outcome", outcome);
        return Map.copyOf(values);
    }

    Attributes traceAttributes() {
        AttributesBuilder attributes = Attributes.builder();
        structuredFields().forEach(attributes::put);
        return attributes.build();
    }

    Attributes metricAttributes() {
        AttributesBuilder attributes = Attributes.builder();
        putIfPresent(attributes, "fashion.operation", operation);
        putIfPresent(attributes, "fashion.peer_service_id", peerServiceId);
        putIfPresent(attributes, "fashion.outcome", outcome);
        return attributes.build();
    }

    private static String safeOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 128 || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException(fieldName + " 只允许安全的非敏感标识");
        }
        return value;
    }

    private static void putIfPresent(Map<String, String> destination, String key, String value) {
        if (value != null) {
            destination.put(key, value);
        }
    }

    private static void putIfPresent(AttributesBuilder destination, String key, String value) {
        if (value != null) {
            destination.put(key, value);
        }
    }
}
