package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Map;
import java.util.Set;

/** 原子创建 User、personal Tenant、OWNER Membership 与必要同意事实。 */
@FunctionalInterface
public interface RegisterUser {

    Result handle(Command command);

    record Command(
            IdentityChannelPort.RegistrationProof registrationProof,
            String displayName,
            String locale,
            String timeZone,
            Map<ConsentPurpose, String> acceptedPolicyVersionIds,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(registrationProof, "registrationProof");
            displayName = DomainPreconditions.requireText(displayName, "displayName");
            locale = DomainPreconditions.requireText(locale, "locale");
            timeZone = DomainPreconditions.requireText(timeZone, "timeZone");
            acceptedPolicyVersionIds = Map.copyOf(DomainPreconditions.requireNonEmpty(
                    acceptedPolicyVersionIds, "acceptedPolicyVersionIds"));
            acceptedPolicyVersionIds.forEach((purpose, versionId) -> {
                DomainPreconditions.requireNonNull(purpose, "acceptedPolicyPurpose");
                DomainPreconditions.requireText(versionId, "acceptedPolicyVersionId");
            });
            DomainPreconditions.require(acceptedPolicyVersionIds.keySet().equals(Set.of(
                            ConsentPurpose.SERVICE_TERMS,
                            ConsentPurpose.PRIVACY_NOTICE)),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.CONSENT_REQUIRED,
                    "registration requires only the current service terms and privacy notice");
            DomainPreconditions.requireNonNull(context, "operationContext");
        }

        @Override
        public String toString() {
            return "Command[registrationProof=" + registrationProof + ", displayName=<redacted>, locale="
                    + locale + ", timeZone=" + timeZone + ", acceptedPolicies="
                    + acceptedPolicyVersionIds.keySet()
                    + ", context=" + context + "]";
        }
    }

    record Result(UserId userId, TenantId personalTenantId, boolean active, AccountView account) {
        public Result {
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(personalTenantId, "personalTenantId");
            DomainPreconditions.requireNonNull(account, "accountView");
            DomainPreconditions.require(userId.equals(account.userId())
                            && personalTenantId.equals(account.tenantId())
                            && active == (account.accountStatus()
                            == com.aiinterviewcoach.domain.identity.UserAccountStatus.ACTIVE),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "registration result and account view are inconsistent");
        }
    }
}
