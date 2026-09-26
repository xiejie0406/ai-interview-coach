package com.ruoyi.aps.application.planning;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceAccessScope;

/** IMP-08 候选生命周期：只标记废弃并保留完整计划历史。 */
public final class PlanCandidateLifecycleService
{
    private final PlanRepository plans;
    private final PlanWorkbenchService workbench;
    private final ApsTransactionOperations transactions;
    private final Clock clock;

    public PlanCandidateLifecycleService(PlanRepository plans, PlanWorkbenchService workbench,
            ApsTransactionOperations transactions)
    {
        this(plans, workbench, transactions, Clock.systemUTC());
    }

    PlanCandidateLifecycleService(PlanRepository plans, PlanWorkbenchService workbench,
            ApsTransactionOperations transactions, Clock clock)
    {
        this.plans = Objects.requireNonNull(plans);
        this.workbench = Objects.requireNonNull(workbench);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    public DiscardedCandidate discard(ResourceAccessScope access, String planVersionId, long expectedRowVersion,
            String reason, String actor)
    {
        String discardReason = requiredReason(reason);
        Instant discardedAt = clock.instant();
        return transactions.required(() -> {
            PlanRepository.PlanRecord locked = plans.findPlanForUpdate(planVersionId)
                    .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.NOT_FOUND, "计划版本不存在"));
            PlanRepository.PlanDetail detail = workbench.detail(access, planVersionId);
            if (!List.of("FEASIBLE", "CONFLICT").contains(locked.status()))
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "只有可行或冲突候选可以废弃");
            if (locked.rowVersion() != expectedRowVersion || detail.plan().rowVersion() != expectedRowVersion)
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "候选版本已变化，请刷新后重试");
            if (plans.discardCandidate(planVersionId, expectedRowVersion, discardedAt, discardReason, actor) != 1)
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "候选已被并发修改");
            return new DiscardedCandidate(workbench.detail(access, planVersionId), discardReason, discardedAt);
        });
    }

    private String requiredReason(String reason)
    {
        if (reason == null || reason.isBlank())
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "废弃原因不能为空");
        String value = reason.trim();
        if (value.length() > 500)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "废弃原因不能超过 500 字符");
        return value;
    }

    public record DiscardedCandidate(PlanRepository.PlanDetail detail, String reason, Instant discardedAt) { }
}
