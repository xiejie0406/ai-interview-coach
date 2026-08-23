package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

@FunctionalInterface
public interface GetCurrentAccount {

    AccountView handle(Query query);

    record Query(QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }
}
