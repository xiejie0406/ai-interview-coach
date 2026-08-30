package com.ruoyi.interview.domain.catalog;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;

/** append-only 发布审核事实；线上指针变化不能替代此证据。 */
public record QuestionPublication(
        ResourceId id,
        TenantId tenantId,
        ResourceId questionId,
        ImmutableVersionRef questionVersion,
        ImmutableVersionRef rubricVersion,
        ResourceId sourceVerificationFactId,
        UserId reviewedBy,
        String reasonCode,
        Instant publishedAt
) {

    public QuestionPublication {
        DomainPreconditions.requireNonNull(id, "publicationId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(questionId, "questionId");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        DomainPreconditions.requireNonNull(sourceVerificationFactId, "sourceVerificationFactId");
        DomainPreconditions.requireNonNull(reviewedBy, "reviewedBy");
        reasonCode = DomainPreconditions.requireText(reasonCode, "publicationReasonCode");
        DomainPreconditions.require(reasonCode.matches("[A-Z][A-Z0-9_]{0,63}"),
                DomainErrorCode.INVALID_ARGUMENT, "publication reason code is invalid");
        DomainPreconditions.requireNonNull(publishedAt, "publishedAt");
    }
}
