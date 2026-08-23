package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.CostBudget;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.TimeBudget;

/** 每次外部模型调用的 tenant、operation、时限和成本门，不含 Secret。 */
public record InvocationContext(
        TenantId tenantId,
        ResourceId businessOperationId,
        CorrelationId correlationId,
        TimeBudget timeBudget,
        CostBudget costBudget
) {

    public InvocationContext {
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
        DomainPreconditions.requireNonNull(correlationId, "correlationId");
        DomainPreconditions.requireNonNull(timeBudget, "timeBudget");
        DomainPreconditions.requireNonNull(costBudget, "costBudget");
    }
}
