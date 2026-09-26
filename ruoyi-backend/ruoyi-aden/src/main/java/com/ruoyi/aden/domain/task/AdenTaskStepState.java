package com.ruoyi.aden.domain.task;

public enum AdenTaskStepState {
    PENDING,
    READY,
    RUNNING,
    WAITING_RETRY,
    SUCCEEDED,
    FAILED,
    CANCELED,
    OUTCOME_UNKNOWN;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == OUTCOME_UNKNOWN;
    }
}
