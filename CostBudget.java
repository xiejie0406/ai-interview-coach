package com.aiinterviewcoach.domain.platform;

/** 外部调用的确定性成本上限。 */
public record CostBudget(Money maximum) {

    public CostBudget {
        DomainPreconditions.requireNonNull(maximum, "maximumCost");
        DomainPreconditions.require(!maximum.isNegative(), DomainErrorCode.INVALID_ARGUMENT,
                "cost budget must not be negative");
    }
}
