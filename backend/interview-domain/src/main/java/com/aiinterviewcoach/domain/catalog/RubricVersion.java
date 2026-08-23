package com.aiinterviewcoach.domain.catalog;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** 与 QuestionVersion 一一对应的不可变评测规则版本。 */
public record RubricVersion(
        ResourceId id,
        TenantId tenantId,
        ResourceId questionVersionId,
        int versionNo,
        String contentHash,
        List<RubricDimension> dimensions,
        String refusalPolicy,
        Instant createdAt
) {

    public RubricVersion {
        DomainPreconditions.requireNonNull(id, "rubricVersionId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
        DomainPreconditions.require(versionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "rubric version number must be positive");
        contentHash = DomainPreconditions.requireText(contentHash, "rubricContentHash");
        dimensions = List.copyOf(DomainPreconditions.requireNonEmpty(dimensions, "rubricDimensions"));
        DomainPreconditions.require(new HashSet<>(dimensions.stream().map(RubricDimension::code).toList()).size()
                        == dimensions.size(),
                DomainErrorCode.INVALID_ARGUMENT, "rubric dimension codes must be unique");
        refusalPolicy = DomainPreconditions.requireText(refusalPolicy, "rubricRefusalPolicy");
        DomainPreconditions.requireNonNull(createdAt, "createdAt");
    }

    public ImmutableVersionRef versionRef() {
        return new ImmutableVersionRef(id, versionNo, contentHash);
    }

    @Override
    public String toString() {
        return "RubricVersion[id=" + id + ", tenantId=" + tenantId + ", questionVersionId="
                + questionVersionId + ", versionNo=" + versionNo + ", contentHash=" + contentHash
                + ", dimensions=<redacted:" + dimensions.size() + ">, refusalPolicy=<redacted>, createdAt="
                + createdAt + "]";
    }
}
