package com.ruoyi.aps.domain.execution;

/**
 * 状态转换所需的服务端事实。调用方必须从锁定后的执行与数量事实构造，不能采用客户端自报值。
 */
public record ExecutionTransitionContext(
        boolean qualityGateRequired,
        boolean hasProducedQuantity,
        boolean remainingDispositionResolved,
        boolean allQualityResolved)
{
    public static ExecutionTransitionContext empty()
    {
        return new ExecutionTransitionContext(false, false, true, true);
    }
}
