package com.ruoyi.interview.domain.platform;

public enum OutboxState {
    PENDING,
    CLAIMED,
    PUBLISHED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}
