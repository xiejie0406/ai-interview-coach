package com.ruoyi.interview.domain.governance;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** append-only 同意事实；撤回通过新事实表达，不能更新历史记录。 */
public record ConsentRecord(
        ResourceId id,
        TenantId tenantId,
        UserId userId,
        ImmutableVersionRef policyVersion,
        ConsentPurpose purpose,
        ConsentAction action,
        String source,
        Instant effectiveAt,
        ResourceId supersedesId
) {

    public ConsentRecord {
        DomainPreconditions.requireNonNull(id, "consentId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(userId, "userId");
        DomainPreconditions.requireNonNull(policyVersion, "policyVersion");
        DomainPreconditions.requireNonNull(purpose, "consentPurpose");
        DomainPreconditions.requireNonNull(action, "consentAction");
        source = DomainPreconditions.requireText(source, "consentSource");
        DomainPreconditions.requireNonNull(effectiveAt, "effectiveAt");
        DomainPreconditions.require(supersedesId == null || !supersedesId.equals(id),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "consent fact cannot supersede itself");
        if (action == ConsentAction.REVOKED) {
            DomainPreconditions.requireNonNull(supersedesId, "revoked consent supersedesId");
        }
    }

    public static ConsentRecord granted(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef policyVersion,
            ConsentPurpose purpose,
            String source,
            Instant effectiveAt
    ) {
        return new ConsentRecord(id, tenantId, userId, policyVersion, purpose, ConsentAction.GRANTED,
                source, effectiveAt, null);
    }

    public static ConsentRecord granted(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef policyVersion,
            ConsentPurpose purpose,
            String source,
            Instant effectiveAt,
            ResourceId supersedesId
    ) {
        return new ConsentRecord(id, tenantId, userId, policyVersion, purpose, ConsentAction.GRANTED,
                source, effectiveAt, DomainPreconditions.requireNonNull(supersedesId, "supersedesId"));
    }

    public static ConsentRecord revoked(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef policyVersion,
            ConsentPurpose purpose,
            String source,
            Instant effectiveAt,
            ResourceId supersedesId
    ) {
        return new ConsentRecord(id, tenantId, userId, policyVersion, purpose, ConsentAction.REVOKED,
                source, effectiveAt, DomainPreconditions.requireNonNull(supersedesId, "supersedesId"));
    }

    public boolean isGranted() {
        return action == ConsentAction.GRANTED;
    }

    public Optional<ResourceId> supersededConsentId() {
        return Optional.ofNullable(supersedesId);
    }
}
