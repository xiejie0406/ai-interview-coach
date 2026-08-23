package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.AccountView;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.identity.UpdateProfile;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 账号 CAS 与不可变 ProfileVersion append 在同一本地事务内完成。 */
public final class DefaultUpdateProfile implements UpdateProfile {

    private static final Set<String> TARGET_ROLES = Set.of(
            "JAVA_BACKEND", "AI_APPLICATION", "AGENT_ENGINEER");
    private static final Set<String> TARGET_LEVELS = Set.of("JUNIOR", "MID", "SENIOR");

    private final IdentityRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultUpdateProfile(
            IdentityRepository repository,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            IdempotencyGuard idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public AccountView handle(Command command) {
        validateEnums(command);
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = requestHash(command);
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "identity.profile.update", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "profile update is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_FAILURE) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                        "profile update replays a recorded failure", false, Map.of());
            }

            var account = repository.findUser(owner.userId()).orElseThrow(DefaultUpdateProfile::notFound);
            var tenant = repository.findTenant(owner.tenantId()).orElseThrow(DefaultUpdateProfile::notFound);
            var membership = repository.findMembership(owner.tenantId(), owner.userId())
                    .orElseThrow(DefaultUpdateProfile::notFound);
            Optional<ProfileVersion> currentProfile = repository.latestProfile(owner.tenantId(), owner.userId());
            AccountView current = IdentityViews.personalOwner(account, tenant, membership, currentProfile);
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return verifiedReplay(current, decision.resourceReferences());
            }

            account.version().requireMatches(command.expectedVersion());
            String displayName = command.displayName().orElse(account.displayName());
            String targetRole = command.targetRole()
                    .orElseGet(() -> currentProfile.map(ProfileVersion::targetRole)
                            .orElse(IdentityProfiles.NOT_DECLARED));
            String targetLevel = command.targetLevel()
                    .orElseGet(() -> currentProfile.map(ProfileVersion::targetLevel)
                            .orElse(IdentityProfiles.NOT_DECLARED));
            String javaExperienceBand = command.javaExperienceYears()
                    .map(IdentityProfiles::exactJavaYears)
                    .orElseGet(() -> currentProfile.map(ProfileVersion::javaExperienceBand)
                            .orElse(IdentityProfiles.NOT_DECLARED));
            String aiExperienceBand = currentProfile.map(ProfileVersion::aiExperienceBand)
                    .orElse(IdentityProfiles.NOT_DECLARED);

            boolean unchanged = currentProfile.isPresent()
                    && account.displayName().equals(displayName)
                    && currentProfile.orElseThrow().targetRole().equals(targetRole)
                    && currentProfile.orElseThrow().targetLevel().equals(targetLevel)
                    && currentProfile.orElseThrow().javaExperienceBand().equals(javaExperienceBand)
                    && currentProfile.orElseThrow().aiExperienceBand().equals(aiExperienceBand);
            if (unchanged) {
                completeIdempotency(requestHash, current, command);
                return current;
            }

            account.rename(displayName, owner.tenantId(), command.expectedVersion(),
                    command.context().eventContext());
            ProfileVersion profile = IdentityProfiles.next(idGenerator.nextResourceId(), account.version(),
                    owner.tenantId(), owner.userId(), targetRole, targetLevel, javaExperienceBand,
                    aiExperienceBand, account.locale(), account.timeZone(), command.context().requestedAt());
            repository.saveUser(account);
            repository.appendProfile(profile, account.version());
            domainEvents.append(account.pullDomainEvents());
            AccountView updated = IdentityViews.personalOwner(account, tenant, membership, Optional.of(profile));
            completeIdempotency(requestHash, updated, command);
            return updated;
        });
    }

    private void completeIdempotency(String requestHash, AccountView view, Command command) {
        Map<String, String> references = new LinkedHashMap<>();
        references.put("userId", view.userId().value());
        references.put("accountVersion", Long.toString(view.version().value()));
        view.latestProfile().ifPresent(profile -> {
            references.put("profileVersionId", profile.versionRef().resourceId().value());
            references.put("profileVersionNo", Integer.toString(profile.versionRef().versionNo()));
            references.put("profileContentHash", profile.versionRef().contentHash());
        });
        idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                "identity.profile.update", requestHash, references, 200, command.context()));
    }

    private static AccountView verifiedReplay(AccountView current, Map<String, String> references) {
        String accountVersion = references.get("accountVersion");
        if (accountVersion == null || !accountVersion.equals(Long.toString(current.version().value()))) {
            throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                    "the original profile response is no longer the current account representation",
                    false, Map.of());
        }
        String profileId = references.get("profileVersionId");
        if (profileId != null) {
            ProfileVersion latest = current.latestProfile().orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "profile replay reference is missing", false, Map.of()));
            if (!profileId.equals(latest.versionRef().resourceId().value())
                    || !references.getOrDefault("profileVersionNo", "")
                    .equals(Integer.toString(latest.versionRef().versionNo()))
                    || !references.getOrDefault("profileContentHash", "")
                    .equals(latest.versionRef().contentHash())) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                        "the original profile version is no longer current", false, Map.of());
            }
        }
        return current;
    }

    private static void validateEnums(Command command) {
        command.targetRole().ifPresent(value -> {
            if (!TARGET_ROLES.contains(value)) {
                throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, "targetRole is unsupported");
            }
        });
        command.targetLevel().ifPresent(value -> {
            if (!TARGET_LEVELS.contains(value)) {
                throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, "targetLevel is unsupported");
            }
        });
    }

    private static String requestHash(Command command) {
        return ServerSideDigest.sha256("identity.profile.update",
                Long.toString(command.expectedVersion().value()),
                command.displayName().orElse("<absent>"),
                command.javaExperienceYears().map(String::valueOf).orElse("<absent>"),
                command.targetRole().orElse("<absent>"),
                command.targetLevel().orElse("<absent>"));
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "current account was not found", false, Map.of());
    }
}
