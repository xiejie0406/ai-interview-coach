package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.evaluation.EvaluationReport;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

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
