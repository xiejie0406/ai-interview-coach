package com.ruoyi.interview.domain.platform;

public enum IdempotencyState {
    PROCESSING,
    SUCCEEDED,
    FAILED_REPLAYABLE,
    EXPIRED
}
