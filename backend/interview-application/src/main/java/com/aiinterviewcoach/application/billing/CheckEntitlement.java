package com.aiinterviewcoach.application.billing;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.UsageQuantity;

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
