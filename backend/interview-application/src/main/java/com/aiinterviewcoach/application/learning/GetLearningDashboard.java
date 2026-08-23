package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

@FunctionalInterface
public interface GetLearningDashboard {
    Result handle(Query query);

    record Query(QueryContext context) {
        public Query { DomainPreconditions.requireNonNull(context, "queryContext"); }
    }

    record Result(LearningDashboardPort.Projection projection) {
        public Result { DomainPreconditions.requireNonNull(projection, "dashboardProjection"); }
    }
}
