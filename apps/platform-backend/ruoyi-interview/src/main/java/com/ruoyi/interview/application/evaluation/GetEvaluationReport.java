package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.evaluation.EvaluationReport;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

@FunctionalInterface
public interface GetEvaluationReport {

    Result handle(Query query);

    record Query(ResourceId reportId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(reportId, "reportId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record Result(EvaluationReport report) {
        public Result {
            DomainPreconditions.requireNonNull(report, "report");
        }
    }
}

