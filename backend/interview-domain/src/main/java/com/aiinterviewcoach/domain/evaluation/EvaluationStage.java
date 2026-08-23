package com.aiinterviewcoach.domain.evaluation;

/** EvaluationView.stage 的稳定阶段投影。 */
public enum EvaluationStage {
    QUEUED,
    EVIDENCE_EXTRACTING,
    RUBRIC_JUDGING,
    REPORT_COMPOSING,
    MANUAL_REVIEW,
    COMPLETE
}
