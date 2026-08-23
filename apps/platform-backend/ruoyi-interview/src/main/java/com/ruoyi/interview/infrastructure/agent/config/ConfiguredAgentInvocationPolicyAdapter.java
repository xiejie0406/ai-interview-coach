package com.ruoyi.interview.infrastructure.agent.config;

import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import com.ruoyi.interview.application.agent.port.AgentInvocationPolicyPort;
import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.CostBudget;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.TimeBudget;

import java.util.Map;

/** capability→预算的只读配置；没有批准预算时拒绝模型调用。 */
public final class ConfiguredAgentInvocationPolicyAdapter implements AgentInvocationPolicyPort {

    private final Map<String, Budget> budgets;

    public ConfiguredAgentInvocationPolicyAdapter(Map<String, Budget> budgets) {
        this.budgets = Map.copyOf(budgets == null ? Map.of() : budgets);
    }

    @Override
    public InvocationContext authorize(
            String capability,
            TenantId tenantId,
            ResourceId businessOperationId,
            CorrelationId correlationId
    ) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("agent capability must not be blank");
        }
        Budget budget = budgets.get(capability);
        if (budget == null) {
            throw new AdapterUnavailableException("agent-invocation-budget");
        }
        return new InvocationContext(tenantId, businessOperationId, correlationId,
                budget.timeBudget(), budget.costBudget());
    }

    public record Budget(TimeBudget timeBudget, CostBudget costBudget) {
        public Budget {
            DomainPreconditions.requireNonNull(timeBudget, "timeBudget");
            DomainPreconditions.requireNonNull(costBudget, "costBudget");
        }
    }
}


