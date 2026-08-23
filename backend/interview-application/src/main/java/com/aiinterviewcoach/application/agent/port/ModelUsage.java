package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.UsageQuantity;

import java.util.List;

/** Provider 返回的原始计量元数据；用户权益结算仍由 billing 的版本化规则裁决。 */
public record ModelUsage(List<UsageQuantity> quantities) {

    public ModelUsage {
        quantities = List.copyOf(DomainPreconditions.requireNonNull(quantities, "modelUsageQuantities"));
    }
}
