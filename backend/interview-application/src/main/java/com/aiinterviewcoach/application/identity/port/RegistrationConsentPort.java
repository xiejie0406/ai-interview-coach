package com.aiinterviewcoach.application.identity.port;

import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.Map;

/** Identity consumer-owned、Governance provider 实现的注册同意原子写入桥。 */
@FunctionalInterface
public interface RegistrationConsentPort {

    void append(Command command);

    record Command(
            TenantId tenantId,
            UserId userId,
            Map<ConsentPurpose, ImmutableVersionRef> acceptedPolicies,
            String source,
            Instant acceptedAt
    ) {
        public Command {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            acceptedPolicies = Map.copyOf(acceptedPolicies == null ? Map.of() : acceptedPolicies);
            DomainPreconditions.require(!acceptedPolicies.isEmpty(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.CONSENT_REQUIRED,
                    "registration consent policies must not be empty");
            source = DomainPreconditions.requireText(source, "consentSource");
            DomainPreconditions.requireNonNull(acceptedAt, "acceptedAt");
        }
    }
}
