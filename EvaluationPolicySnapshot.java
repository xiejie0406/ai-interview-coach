package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** 一次评估固定的 config、Prompt、Schema、Provider route 与 Rubric 版本快照。 */
public record EvaluationPolicySnapshot(
        String configVersionId,
        com.aiinterviewcoach.domain.platform.PromptSchemaPin evidencePin,
        com.aiinterviewcoach.domain.platform.PromptSchemaPin judgePin,
        com.aiinterviewcoach.domain.platform.PromptSchemaPin reportPin,
        com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot providerPolicy,
        String rubricVersionId
) {

    public EvaluationPolicySnapshot {
        configVersionId = DomainPreconditions.requireText(configVersionId, "configVersionId");
        DomainPreconditions.require(configVersionId.length() <= 128,
                com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "configVersionId is too long");
        DomainPreconditions.requireNonNull(evidencePin, "evidencePin");
        DomainPreconditions.requireNonNull(judgePin, "judgePin");
        DomainPreconditions.requireNonNull(reportPin, "reportPin");
        DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
        rubricVersionId = DomainPreconditions.requireText(rubricVersionId, "rubricVersionId");
        DomainPreconditions.require(rubricVersionId.length() <= 128,
                com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "rubricVersionId is too long");
    }
}
