package com.aiinterviewcoach.domain.platform;

public enum IdempotencyState {
    PROCESSING,
    SUCCEEDED,
    FAILED_REPLAYABLE,
    EXPIRED
}
