package com.ruoyi.fashion.application.agent.run;

public final class FashionRuntimeException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public FashionRuntimeException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
