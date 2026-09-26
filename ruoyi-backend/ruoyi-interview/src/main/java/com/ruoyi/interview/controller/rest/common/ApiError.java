package com.ruoyi.interview.controller.rest.common;

import java.util.Map;
import java.util.Objects;

/** Public error payload. Sensitive implementation details never cross this boundary. */
public record ApiError(
        String code,
        String userMessage,
        boolean retryable,
        String correlationId,
        Map<String, Object> details) {

    public ApiError {
        code = Objects.requireNonNull(code, "code must not be null");
        userMessage = Objects.requireNonNull(userMessage, "userMessage must not be null");
        correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}

