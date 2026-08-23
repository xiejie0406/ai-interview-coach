package com.aiinterviewcoach.domain.practice;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** 已提交回答的不可变版本。重复/重练不得覆盖该对象。 */
public record AnswerVersion(
        ResourceId id,
        TenantId tenantId,
        ResourceId attemptId,
        int versionNo,
        AnswerSource source,
        String text,
        String contentHash,
        UserId submittedBy,
        Instant submittedAt,
        Optional<ResourceId> supersedesId
) {

    public AnswerVersion {
        DomainPreconditions.requireNonNull(id, "answerVersionId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(attemptId, "attemptId");
        DomainPreconditions.require(versionNo > 0, com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "answer version number must be positive");
        DomainPreconditions.requireNonNull(source, "answerSource");
        text = DomainPreconditions.requireText(text, "answerText");
        contentHash = DomainPreconditions.requireText(contentHash, "answerContentHash");
        DomainPreconditions.requireNonNull(submittedBy, "submittedBy");
        DomainPreconditions.requireNonNull(submittedAt, "submittedAt");
        supersedesId = supersedesId == null ? Optional.empty() : supersedesId;
    }

    @Override
    public String toString() {
        return "AnswerVersion[id=" + id + ", tenantId=" + tenantId + ", attemptId=" + attemptId
                + ", versionNo=" + versionNo + ", source=" + source
                + ", text=<redacted>, contentHash=<redacted>, submittedBy=" + submittedBy
                + ", submittedAt=" + submittedAt + "]";
    }
}
