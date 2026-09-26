package com.ruoyi.interview.application.agent.port;

import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.CostBudget;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.TimeBudget;

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
