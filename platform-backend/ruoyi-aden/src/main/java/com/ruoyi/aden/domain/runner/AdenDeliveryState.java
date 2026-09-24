package com.ruoyi.aden.domain.runner;

public enum AdenDeliveryState {
    READY, LEASED, RUNNING, COMPLETED, FAILED_RETRYABLE, FAILED_FINAL, CANCELED, OUTCOME_UNKNOWN;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED_FINAL || this == CANCELED || this == OUTCOME_UNKNOWN;
    }
}
