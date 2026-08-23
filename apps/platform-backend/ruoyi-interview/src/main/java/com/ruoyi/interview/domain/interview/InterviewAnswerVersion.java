package com.ruoyi.interview.domain.interview;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** 面试轮次的不可变确认回答；ASR final 未经用户确认不能创建本对象。 */
public record InterviewAnswerVersion(
        ResourceId id,
        TenantId tenantId,
        ResourceId sessionId,
        ResourceId turnId,
        int versionNo,
        InterviewAnswerSource source,
        String text,
        String contentHash,
        Optional<ResourceId> transcriptVersionId,
        UserId confirmedBy,
        Instant confirmedAt,
        Optional<ResourceId> supersedesId
) {

    public InterviewAnswerVersion {
        DomainPreconditions.requireNonNull(id, "interviewAnswerVersionId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(sessionId, "sessionId");
        DomainPreconditions.requireNonNull(turnId, "turnId");
        DomainPreconditions.require(versionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "answer version number must be positive");
        DomainPreconditions.requireNonNull(source, "answerSource");
        text = DomainPreconditions.requireText(text, "answerText");
        contentHash = DomainPreconditions.requireText(contentHash, "answerContentHash");
        transcriptVersionId = transcriptVersionId == null ? Optional.empty() : transcriptVersionId;
        DomainPreconditions.require(source != InterviewAnswerSource.CONFIRMED_TRANSCRIPT
                        || transcriptVersionId.isPresent(),
                DomainErrorCode.INVALID_ARGUMENT,
                "confirmed transcript answer requires transcript version reference");
        DomainPreconditions.requireNonNull(confirmedBy, "confirmedBy");
        DomainPreconditions.requireNonNull(confirmedAt, "confirmedAt");
        supersedesId = supersedesId == null ? Optional.empty() : supersedesId;
    }

    @Override
    public String toString() {
        return "InterviewAnswerVersion[id=" + id + ", tenantId=" + tenantId + ", sessionId=" + sessionId
                + ", turnId=" + turnId + ", versionNo=" + versionNo + ", source=" + source
                + ", text=<redacted>, contentHash=<redacted>, transcriptVersionId="
                + transcriptVersionId + ", confirmedBy=" + confirmedBy + ", confirmedAt=" + confirmedAt + "]";
    }
}
