package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.evaluation.EvaluationRun;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

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
