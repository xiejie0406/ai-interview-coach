package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.evaluation.EvaluationRun;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;

@FunctionalInterface
public interface GetEvaluation {
    Result handle(Query query);

    record Query(ResourceId evaluationId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record Result(EvaluationRun evaluation, Optional<String> streamCursor) {
        public Result {
            DomainPreconditions.requireNonNull(evaluation, "evaluation");
            streamCursor = streamCursor == null ? Optional.empty() : streamCursor;
        }
    }
}
