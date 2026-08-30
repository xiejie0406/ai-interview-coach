package com.ruoyi.interview.application.evaluation.internal;

import com.ruoyi.interview.application.evaluation.EvaluationRepository;
import com.ruoyi.interview.application.evaluation.GetInterviewReport;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

/** owner-scoped interviewId -> report root；禁止把 interviewId 当 reportId。 */
public final class DefaultGetInterviewReport implements GetInterviewReport {

    private final EvaluationRepository repository;

    public DefaultGetInterviewReport(EvaluationRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var principal = query.context().principal();
        var report = repository.findReportByInterview(principal.tenantId(), query.interviewId())
                .orElseThrow(DefaultGetInterviewReport::notFound);
        if (!report.userId().equals(principal.userId())) {
            throw notFound();
        }
        return new Result(report);
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "interview report was not found", false, Map.of());
    }
}
