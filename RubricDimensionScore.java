package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.List;

/** rubric-judgement-v1 的维度判断；历史类名保留，数值 score 语义已移除。 */
public record RubricDimensionScore(
        String dimensionId,
        RubricJudgement judgement,
        RubricConfidence confidence,
        boolean insufficientEvidence,
        List<String> reasonCodes,
        List<ResourceId> evidenceIds
) {

    public RubricDimensionScore {
        dimensionId = DomainPreconditions.requireText(dimensionId, "dimensionId");
        DomainPreconditions.requireNonNull(judgement, "judgement");
        DomainPreconditions.requireNonNull(confidence, "confidence");
        DomainPreconditions.require(insufficientEvidence == (judgement == RubricJudgement.INSUFFICIENT),
                DomainErrorCode.INVALID_ARGUMENT,
                "insufficientEvidence must agree with INSUFFICIENT judgement");
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        DomainPreconditions.require(reasonCodes.size() <= 16, DomainErrorCode.INVALID_ARGUMENT,
                "reason code count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(reasonCodes).size() == reasonCodes.size(),
                DomainErrorCode.INVALID_ARGUMENT, "reason codes must be unique");
        reasonCodes.forEach(value -> {
            DomainPreconditions.requireText(value, "reasonCode");
            DomainPreconditions.require(value.length() <= 96, DomainErrorCode.INVALID_ARGUMENT,
                    "reasonCode is too long");
        });
        evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
        DomainPreconditions.require(evidenceIds.size() <= 32, DomainErrorCode.INVALID_ARGUMENT,
                "evidence reference count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(evidenceIds).size() == evidenceIds.size(),
                DomainErrorCode.INVALID_ARGUMENT, "evidence IDs must be unique");
    }

    @Override
    public String toString() {
        return "RubricDimensionScore[dimensionId=" + dimensionId + ", judgement=" + judgement
                + ", confidence=" + confidence + ", insufficientEvidence=" + insufficientEvidence
                + ", reasonCodes=<redacted>, evidenceIdCount=" + evidenceIds.size() + "]";
    }
}
