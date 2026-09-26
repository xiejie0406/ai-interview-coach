package com.ruoyi.aden.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdenErrorEnvelope(
        int code,
        String msg,
        Object data,
        String errorCode,
        boolean retryable,
        String correlationId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details) {

    public AdenErrorEnvelope {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
