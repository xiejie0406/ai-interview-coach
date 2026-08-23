package com.ruoyi.interview.application.billing;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.UsageQuantity;

@FunctionalInterface
public interface CheckEntitlement {

    EntitlementView handle(Query query);

    record Query(UsageQuantity required, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(required, "requiredUsage");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }
}
