package com.ruoyi.interview.domain.interview;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.UsageQuantity;

/** 计划确认前向用户展示的确定性预估；不等同最终结算。 */
public record UsageEstimate(UsageQuantity quantity, String ruleVersion) {

    public UsageEstimate {
        DomainPreconditions.requireNonNull(quantity, "estimatedUsage");
        ruleVersion = DomainPreconditions.requireText(ruleVersion, "usageEstimateRuleVersion");
    }
}
