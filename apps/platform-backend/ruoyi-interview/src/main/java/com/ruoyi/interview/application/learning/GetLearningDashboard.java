package com.ruoyi.interview.application.learning;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

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
