package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.RegisterUser;
import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.identity.port.RegistrationPolicyPort;
import com.aiinterviewcoach.application.identity.port.RegistrationConsentPort;
import com.aiinterviewcoach.application.identity.port.RegistrationIdempotencyPort;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.identity.MembershipRole;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.identity.Tenant;
import com.aiinterviewcoach.domain.identity.UserAccount;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 原子创建账户、个人租户、OWNER membership 与注册政策同意。 */
public final class DefaultRegisterUser implements RegisterUser {

    private final IdentityChannelPort identityChannel;
    private final RegistrationPolicyPort registrationPolicies;
    private final IdentityRepository identityRepository;
    private final RegistrationIdentifierPort registrationIdentifiers;
    private final RegistrationConsentPort registrationConsents;
    private final IdGeneratorPort idGenerator;
    private final RegistrationIdempotencyPort idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultRegisterUser(
            IdentityChannelPort identityChannel,
            RegistrationPolicyPort registrationPolicies,
            IdentityRepository identityRepository,
            RegistrationIdentifierPort registrationIdentifiers,
            RegistrationConsentPort registrationConsents,
            IdGeneratorPort idGenerator,
            RegistrationIdempotencyPort idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        this.identityChannel = java.util.Objects.requireNonNull(identityChannel);
        this.registrationPolicies = java.util.Objects.requireNonNull(registrationPolicies);
        this.identityRepository = java.util.Objects.requireNonNull(identityRepository);
        this.registrationIdentifiers = java.util.Objects.requireNonNull(registrationIdentifiers);
        this.registrationConsents = java.util.Objects.requireNonNull(registrationConsents);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            IdentityChannelPort.RegistrationDecision verification = identityChannel.verifyRegistration(
                    command.registrationProof());
            if (!verification.verified()) {
                throw new DomainException(com.aiinterviewcoach.domain.platform.DomainErrorCode.POLICY_DENIED,
                        "registration proof was rejected");
            }
            if (!verification.normalizedIdentifierHash().equals(command.context().principalScopeHash())) {
                throw new DomainException(com.aiinterviewcoach.domain.platform.DomainErrorCode.POLICY_DENIED,
                        "registration principal scope does not match the verified identifier");
            }
            String requestHash = ServerSideDigest.sha256(
                    "register", verification.normalizedIdentifierHash(), command.displayName(), command.locale(),
                    command.timeZone(), command.acceptedPolicyVersionIds().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                            .collect(Collectors.joining(",")));
            IdempotencyGuard.Decision decision = idempotency.begin(new RegistrationIdempotencyPort.BeginCommand(
                    "identity.register", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "registration request is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String userId = decision.resourceReferences().get("userId");
                String tenantId = decision.resourceReferences().get("personalTenantId");
                if (userId == null || tenantId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "registration replay has incomplete resource references", false, Map.of());
                }
                var resolvedUserId = com.aiinterviewcoach.domain.platform.UserId.of(userId);
                var resolvedTenantId = com.aiinterviewcoach.domain.platform.TenantId.of(tenantId);
                UserAccount replayAccount = identityRepository.findUser(resolvedUserId).orElseThrow(() ->
                        consistency("registration replay account is missing"));
                Tenant replayTenant = identityRepository.findTenant(resolvedTenantId).orElseThrow(() ->
                        consistency("registration replay tenant is missing"));
                Membership replayMembership = identityRepository.findMembership(resolvedTenantId, resolvedUserId)
                        .orElseThrow(() -> consistency("registration replay membership is missing"));
                Optional<ProfileVersion> replayProfile = identityRepository.latestProfile(
                        resolvedTenantId, resolvedUserId);
                var accountView = IdentityViews.personalOwner(
                        replayAccount, replayTenant, replayMembership, replayProfile);
                return new Result(resolvedUserId, resolvedTenantId,
                        replayAccount.canAuthenticate(), accountView);
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_FAILURE) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                        "registration replays a recorded failure", false, Map.of());
            }
            if (registrationIdentifiers.findOwner(verification.normalizedIdentifierHash()).isPresent()) {
                throw new DomainException(com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_STATE,
                        "registration identifier is already registered");
            }
            Map<ConsentPurpose, ImmutableVersionRef> required =
                    registrationPolicies.requiredPolicies(command.locale(), command.context().requestedAt());
            Map<ConsentPurpose, String> requiredVersionIds = required.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                            entry -> entry.getValue().resourceId().value()));
            if (!requiredVersionIds.equals(command.acceptedPolicyVersionIds())) {
                throw new DomainException(com.aiinterviewcoach.domain.platform.DomainErrorCode.CONSENT_REQUIRED,
                        "registration requires the current service terms and privacy notice");
            }

            var userId = idGenerator.nextUserId();
            var tenantId = idGenerator.nextTenantId();
            ResourceId membershipId = idGenerator.nextResourceId();
            Tenant tenant = Tenant.createPersonal(tenantId, userId, command.displayName(), command.context().eventContext());
            UserAccount account = UserAccount.register(userId, command.locale(), command.timeZone(),
                    command.displayName(), tenantId, command.context().eventContext());
            account.activate(tenantId, account.version(), command.context().eventContext());
            Membership membership = Membership.join(membershipId, tenantId, userId, MembershipRole.OWNER,
                    command.context().requestedAt(), command.context().eventContext());
            ProfileVersion profile = IdentityProfiles.initial(idGenerator.nextResourceId(), tenantId, account,
                    command.context().requestedAt());
            identityRepository.saveUser(account);
            identityRepository.saveTenant(tenant);
            identityRepository.saveMembership(membership);
            identityRepository.appendProfile(profile, account.version());
            registrationIdentifiers.bind(verification.normalizedIdentifierHash(), userId);
            identityChannel.bindRegistrationCredential(command.registrationProof(), userId);
            registrationConsents.append(new RegistrationConsentPort.Command(
                    tenantId, userId, required, "registration",
                    command.context().requestedAt()));
            domainEvents.append(tenant.pullDomainEvents());
            domainEvents.append(account.pullDomainEvents());
            domainEvents.append(membership.pullDomainEvents());
            idempotency.succeed(new RegistrationIdempotencyPort.CompleteCommand(
                    "identity.register", requestHash,
                    Map.of("userId", userId.value(), "personalTenantId", tenantId.value(),
                            "profileVersionId", profile.versionRef().resourceId().value()), 201,
                    command.context()));
            var accountView = IdentityViews.personalOwner(account, tenant, membership, Optional.of(profile));
            return new Result(userId, tenantId, account.canAuthenticate(), accountView);
        });
    }

    private static ApplicationException consistency(String message) {
        return new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                message, false, Map.of());
    }
}
