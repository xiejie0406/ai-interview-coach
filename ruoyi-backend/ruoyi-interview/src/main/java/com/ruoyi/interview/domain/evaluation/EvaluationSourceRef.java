package com.ruoyi.interview.domain.evaluation;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;

/** 评估输入的稳定引用与摘要；不包含回答或转写正文。 */
public record EvaluationSourceRef(
        ResourceId answerVersionId,
        String answerHash,
        ResourceId interviewId,
        ResourceId planId,
        long planVersionNo,
        Optional<ResourceId> confirmedTranscriptVersionId
) {

    public EvaluationSourceRef {
        DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
        answerHash = requireSha256(answerHash, "answerHash");
        DomainPreconditions.requireNonNull(interviewId, "interviewId");
        DomainPreconditions.requireNonNull(planId, "planId");
        DomainPreconditions.require(planVersionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "planVersionNo must be positive");
        confirmedTranscriptVersionId = confirmedTranscriptVersionId == null
                ? Optional.empty() : confirmedTranscriptVersionId;
    }

    private static String requireSha256(String value, String name) {
        value = DomainPreconditions.requireText(value, name);
        DomainPreconditions.require(value.matches("(?i)^[a-f0-9]{64}$"), DomainErrorCode.INVALID_ARGUMENT,
                name + " must be a SHA-256 hex digest");
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
