package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.domain.evaluation.EvaluationReport;
import com.aiinterviewcoach.domain.evaluation.EvaluationRun;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

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
