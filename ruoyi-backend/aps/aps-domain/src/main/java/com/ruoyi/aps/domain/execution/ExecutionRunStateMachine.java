package com.ruoyi.aps.domain.execution;

import java.util.Objects;

/** IMP09 批准的 M25 单向状态机。 */
public final class ExecutionRunStateMachine
{
    private ExecutionRunStateMachine()
    {
    }

    public static ExecutionRunStatus transition(ExecutionRunStatus current, ExecutionAction action,
            ExecutionTransitionContext context)
    {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");

        return switch (current)
        {
            case READY -> fromReady(action);
            case RUNNING -> fromRunning(action, context);
            case PAUSED -> fromPaused(action, context);
            case WAIT_QUALITY -> fromWaitQuality(action, context);
            case COMPLETED, CANCELLED -> throw illegal(current, action, "终态运行不可恢复或改写");
        };
    }

    private static ExecutionRunStatus fromReady(ExecutionAction action)
    {
        return switch (action)
        {
            case START -> ExecutionRunStatus.RUNNING;
            case CANCEL -> ExecutionRunStatus.CANCELLED;
            default -> throw illegal(ExecutionRunStatus.READY, action, "未开工运行只允许开工或取消");
        };
    }

    private static ExecutionRunStatus fromRunning(ExecutionAction action, ExecutionTransitionContext context)
    {
        return switch (action)
        {
            case PAUSE -> ExecutionRunStatus.PAUSED;
            case FINISH_PROCESSING -> context.qualityGateRequired() || !context.allQualityResolved()
                    ? ExecutionRunStatus.WAIT_QUALITY : ExecutionRunStatus.COMPLETED;
            default -> throw illegal(ExecutionRunStatus.RUNNING, action, "运行中不得直接取消、恢复或质量完结");
        };
    }

    private static ExecutionRunStatus fromPaused(ExecutionAction action, ExecutionTransitionContext context)
    {
        return switch (action)
        {
            case RESUME -> ExecutionRunStatus.RUNNING;
            case CANCEL -> {
                if (context.hasProducedQuantity() && !context.remainingDispositionResolved())
                {
                    throw illegal(ExecutionRunStatus.PAUSED, action, "已有产出时必须先完成剩余数量处置");
                }
                yield ExecutionRunStatus.CANCELLED;
            }
            default -> throw illegal(ExecutionRunStatus.PAUSED, action, "暂停运行只允许恢复或受控取消");
        };
    }

    private static ExecutionRunStatus fromWaitQuality(ExecutionAction action, ExecutionTransitionContext context)
    {
        if (action != ExecutionAction.COMPLETE_QUALITY)
        {
            throw illegal(ExecutionRunStatus.WAIT_QUALITY, action, "待质检运行只允许在全部处置完成后完结");
        }
        if (!context.allQualityResolved())
        {
            throw illegal(ExecutionRunStatus.WAIT_QUALITY, action, "仍有待检或隔离数量");
        }
        return ExecutionRunStatus.COMPLETED;
    }

    private static IllegalStateException illegal(ExecutionRunStatus current, ExecutionAction action, String reason)
    {
        return new IllegalStateException("非法执行状态转换 " + current + " -> " + action + "：" + reason);
    }
}
