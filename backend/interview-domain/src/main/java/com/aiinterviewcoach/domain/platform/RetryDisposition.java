package com.aiinterviewcoach.domain.platform;

/** 外部或异步失败的受控重试分类。 */
public enum RetryDisposition {
    NOT_RETRYABLE,
    SAFE_IMMEDIATE,
    SAFE_BACKOFF,
    REQUIRES_HUMAN;

    public boolean permitsAutomaticRetry() {
        return this == SAFE_IMMEDIATE || this == SAFE_BACKOFF;
    }
}
