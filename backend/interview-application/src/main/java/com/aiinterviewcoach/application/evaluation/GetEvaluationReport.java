package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.evaluation.EvaluationReport;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

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

