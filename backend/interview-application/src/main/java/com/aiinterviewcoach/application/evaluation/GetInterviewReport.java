package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.evaluation.EvaluationReport;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

@FunctionalInterface
public interface GetInterviewReport {
    Result handle(Query query);

    record Query(ResourceId interviewId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(interviewId, "interviewId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record Result(EvaluationReport report) {
        public Result {
            DomainPreconditions.requireNonNull(report, "report");
        }
    }
}
