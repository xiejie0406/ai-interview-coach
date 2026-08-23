package com.ruoyi.interview.application.governance.port;

import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** Voice/Agent 等 consumer 只查询折叠后的同意决定，不访问 ConsentRepository。 */
public interface ConsentQueryPort {

    ConsentDecision current(TenantId tenantId, UserId userId, ConsentPurpose purpose, Instant at);

    record ConsentDecision(
            ConsentPurpose purpose,
            boolean granted,
            Optional<ResourceId> consentRecordId,
            Optional<ImmutableVersionRef> policyVersion,
            Optional<Instant> effectiveAt
    ) {
        public ConsentDecision {
            DomainPreconditions.requireNonNull(purpose, "consentPurpose");
            consentRecordId = consentRecordId == null ? Optional.empty() : consentRecordId;
            policyVersion = policyVersion == null ? Optional.empty() : policyVersion;
            effectiveAt = effectiveAt == null ? Optional.empty() : effectiveAt;
            DomainPreconditions.require(granted == (consentRecordId.isPresent()
                            && policyVersion.isPresent() && effectiveAt.isPresent()),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "consent decision fields are inconsistent");
            DomainPreconditions.require(granted || (consentRecordId.isEmpty()
                            && policyVersion.isEmpty() && effectiveAt.isEmpty()),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "denied consent decision must not retain effective references");
        }
    }
}
