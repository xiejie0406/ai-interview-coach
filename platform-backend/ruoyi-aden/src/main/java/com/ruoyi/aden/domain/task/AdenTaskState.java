package com.ruoyi.aden.domain.task;

/** Aden 自动化任务的服务端权威执行状态。 */
public enum AdenTaskState {
    DRAFT,
    VALIDATING,
    QUEUED,
    RUNNING,
    WAITING_USER,
    WAITING_EXTERNAL,
    CANCEL_REQUESTED,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
