package com.aiinterviewcoach.adapters.outbound.agent.config;

import com.aiinterviewcoach.adapters.outbound.AdapterUnavailableException;
import com.aiinterviewcoach.application.agent.port.AgentInvocationPolicyPort;
import com.aiinterviewcoach.application.agent.port.InvocationContext;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.CostBudget;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.TimeBudget;

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
