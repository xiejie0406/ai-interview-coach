package com.ruoyi.interview.domain.evaluation;

import com.ruoyi.interview.domain.platform.DomainPreconditions;

/** 一次评估固定的 config、Prompt、Schema、Provider route 与 Rubric 版本快照。 */
public record EvaluationPolicySnapshot(
        String configVersionId,
        com.ruoyi.interview.domain.platform.PromptSchemaPin evidencePin,
        com.ruoyi.interview.domain.platform.PromptSchemaPin judgePin,
        com.ruoyi.interview.domain.platform.PromptSchemaPin reportPin,
        com.ruoyi.interview.domain.platform.ProviderPolicySnapshot providerPolicy,
        String rubricVersionId
) {

    public EvaluationPolicySnapshot {
        configVersionId = DomainPreconditions.requireText(configVersionId, "configVersionId");
        DomainPreconditions.require(configVersionId.length() <= 128,
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "configVersionId is too long");
        DomainPreconditions.requireNonNull(evidencePin, "evidencePin");
        DomainPreconditions.requireNonNull(judgePin, "judgePin");
        DomainPreconditions.requireNonNull(reportPin, "reportPin");
        DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
        rubricVersionId = DomainPreconditions.requireText(rubricVersionId, "rubricVersionId");
        DomainPreconditions.require(rubricVersionId.length() <= 128,
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "rubricVersionId is too long");
    }
}
