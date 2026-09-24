package com.ruoyi.aps.domain.execution;

/** M25 执行运行状态；终态运行不恢复为活动状态。 */
public enum ExecutionRunStatus
{
    READY,
    RUNNING,
    PAUSED,
    WAIT_QUALITY,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal()
    {
        return this == COMPLETED || this == CANCELLED;
    }
}
