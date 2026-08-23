package com.aiinterviewcoach.application.evaluation.internal;

import com.aiinterviewcoach.application.evaluation.EvaluationRepository;
import com.aiinterviewcoach.application.evaluation.GetEvaluationReport;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** Reads a report only within the authenticated tenant and owner scope. */
public final class DefaultGetEvaluationReport implements GetEvaluationReport {

    private final EvaluationRepository repository;

    public DefaultGetEvaluationReport(EvaluationRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var principal = query.context().principal();
        var report = repository.findReport(principal.tenantId(), query.reportId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "evaluation report was not found", false, Map.of()));
        if (!report.userId().equals(principal.userId())) {
            throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                    "evaluation report was not found", false, Map.of());
        }
        return new Result(report);
    }
}

