package com.aiinterviewcoach.domain.catalog;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;

/** 题目版本的来源和许可快照；发布版本不能脱离来源治理。 */
public record ContentSourceReference(
        ResourceId sourceId,
        ImmutableVersionRef sourceVersion,
        String licenseCode,
        ResourceId verificationFactId,
        Instant verifiedAt
) {

    public ContentSourceReference {
        DomainPreconditions.requireNonNull(sourceId, "sourceId");
        DomainPreconditions.requireNonNull(sourceVersion, "sourceVersion");
        licenseCode = DomainPreconditions.requireText(licenseCode, "licenseCode");
        DomainPreconditions.requireNonNull(verificationFactId, "sourceVerificationFactId");
        DomainPreconditions.requireNonNull(verifiedAt, "sourceVerifiedAt");
    }
}
