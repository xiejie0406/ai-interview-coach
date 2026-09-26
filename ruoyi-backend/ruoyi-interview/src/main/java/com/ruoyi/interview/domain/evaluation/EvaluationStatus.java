package com.ruoyi.interview.domain.evaluation;

/** 与公共 Evaluation API 对齐的运行状态；处理阶段由 EvaluationStage 单独表达。 */
public enum EvaluationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCELLED
}
