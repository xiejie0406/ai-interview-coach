package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.domain.evaluation.EvaluationReport;
import com.ruoyi.interview.domain.evaluation.EvaluationRun;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

/** Evaluation/Report 的 tenant-scoped persistence owner port。 */
public interface EvaluationRepository {

    Optional<EvaluationRun> findRun(TenantId tenantId, ResourceId evaluationId);

    void saveRun(EvaluationRun evaluationRun);

    Optional<EvaluationReport> findReport(TenantId tenantId, ResourceId reportId);

    Optional<EvaluationReport> findReportByEvaluation(TenantId tenantId, ResourceId evaluationId);

    Optional<EvaluationReport> findReportByInterview(TenantId tenantId, ResourceId interviewId);

    void saveReport(EvaluationReport report);
}
