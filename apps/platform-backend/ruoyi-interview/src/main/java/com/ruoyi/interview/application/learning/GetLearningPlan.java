package com.ruoyi.interview.application.learning;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.learning.LearningPlan;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

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

