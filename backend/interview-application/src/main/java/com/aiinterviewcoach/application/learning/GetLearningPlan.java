package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.learning.LearningPlan;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

@FunctionalInterface
public interface GetLearningPlan {

    Result handle(Query query);

    record Query(ResourceId learningPlanId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(learningPlanId, "learningPlanId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record Result(LearningPlan plan) {
        public Result {
            DomainPreconditions.requireNonNull(plan, "learningPlan");
        }
    }
}

