package com.ruoyi.interview.domain.interview;

public enum SessionState {
    READY,
    IN_PROGRESS,
    PAUSED,
    FAILED_RECOVERABLE,
    COMPLETING,
    COMPLETED,
    CANCELLED,
    FAILED_FINAL
}
