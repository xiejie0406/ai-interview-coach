package com.ruoyi.aden.application.error;

import java.util.Map;

public class AdenApplicationException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> details;

    public AdenApplicationException(String errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public AdenApplicationException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String errorCode() { return errorCode; }
    public Map<String, Object> details() { return details; }
}
