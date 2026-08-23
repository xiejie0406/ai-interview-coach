package com.ruoyi.interview.domain.platform;

public enum JobState {
    PENDING,
    RUNNING,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCEL_REQUESTED,
    CANCELLED,
    SUCCEEDED
}
