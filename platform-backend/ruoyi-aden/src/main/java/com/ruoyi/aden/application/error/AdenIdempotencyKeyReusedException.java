package com.ruoyi.aden.application.error;

public final class AdenIdempotencyKeyReusedException extends AdenApplicationException {
    public AdenIdempotencyKeyReusedException() {
        super("ADEN_IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同请求");
    }
}
