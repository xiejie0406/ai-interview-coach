package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.application.error.AdenNotFoundException;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.application.workspace.AdenWorkspaceAccessGuard;
import com.ruoyi.aden.domain.runner.AdenCapabilitySnapshot;
import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenCredentialStatus;
import com.ruoyi.aden.domain.runner.AdenRunnerCredential;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenCorrelationId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.security.AdenRunnerCredentialCodec;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 管理员签发和吊销 Runner；明文设备 Secret 不进入 repository、事件或审计。 */
public class AdenRunnerAdministrationService {
    public static final String ENROLL_PERMISSION = "aden:runner:enroll";
    public static final String REVOKE_PERMISSION = "aden:runner:revoke";
    private static final String OUTBOX_CONSUMER = "OPERATOR_SSE";

    private final AdenRunnerLedgerRepository repository;
    private final AdenWorkspaceAccessGuard accessGuard;
    private final AdenRunnerCredentialCodec credentialCodec;
    private final AdenRunnerPepperProvider pepperProvider;
    private final AdenRequestFingerprint json;
    private final AdenIdGenerator ids;
    private final Clock clock;
    private final Duration credentialTtl;

    public AdenRunnerAdministrationService(AdenRunnerLedgerRepository repository,
                                           AdenWorkspaceAccessGuard accessGuard,
                                           AdenRunnerCredentialCodec credentialCodec,
                                           AdenRunnerPepperProvider pepperProvider,
                                           AdenRequestFingerprint json,
                                           AdenIdGenerator ids,
                                           Clock clock,
                                           Duration credentialTtl) {
        this.repository = Objects.requireNonNull(repository);
        this.accessGuard = Objects.requireNonNull(accessGuard);
        this.credentialCodec = Objects.requireNonNull(credentialCodec);
        this.pepperProvider = Objects.requireNonNull(pepperProvider);
        this.json = Objects.requireNonNull(json);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        this.credentialTtl = Objects.requireNonNull(credentialTtl);
        if (credentialTtl.isZero() || credentialTtl.isNegative()) {
            throw new IllegalArgumentException("credentialTtl 必须为正数");
        }
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public Enrollment enroll(Enroll command) {
        Objects.requireNonNull(command);
        accessGuard.requireWorkspace(command.principal(), ENROLL_PERMISSION, command.workspaceId(),
                AdenWorkspaceAccessGuard.OWNER_ONLY, command.correlationId().value());
        if (!command.capabilities().equals(Set.of(AdenCapabilityCode.CORE))) {
            throw new IllegalArgumentException("当前只允许合成 CORE Runner");
        }
        Instant now = clock.instant();
        long sequence = repository.lockWorkspaceEventSequence(command.workspaceId());
        AdenRunnerId runnerId = new AdenRunnerId(ids.nextId());
        AdenCredentialId credentialId = new AdenCredentialId(ids.nextId());
        String pepperKeyId = pepperProvider.currentKeyId();
        var issued = credentialCodec.issue(
                credentialId, pepperKeyId, pepperProvider.pepper(pepperKeyId));
        var runner = new AdenRunnerLedgerRepository.RunnerFact(
                command.workspaceId(), runnerId, normalizeName(command.displayName()), "OFFLINE",
                Set.copyOf(command.capabilities()), 0, 1, 1,
                command.principal().userId(), now, now);
        var credential = new AdenRunnerCredential(
                command.workspaceId(), credentialId, runnerId, 1, issued.keyedDigest(),
                issued.pepperKeyId(), AdenCredentialStatus.ACTIVE, now, now.plus(credentialTtl));
        repository.insertRunner(runner);
        repository.insertCredential(credential);
        appendEvent(sequence, runner, "aden.runner.status-changed.v1", "OPERATOR",
                Long.toString(command.principal().userId()), command.correlationId().value(), now,
                Map.of("to", "OFFLINE", "runnerId", runnerId.value()));
        repository.insertAudit(new AdenRunnerLedgerRepository.RunnerAudit(
                command.workspaceId(), ids.nextId(), "RUNNER_ENROLLED", runnerId, "OPERATOR",
                Long.toString(command.principal().userId()), command.correlationId().value(),
                json.canonicalJson(Map.of("capabilities", command.capabilities())), now));
        return new Enrollment(command.workspaceId(), runnerId, credentialId,
                issued.bearer(), 1, credential.expiresAt(), now);
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public void revoke(Revoke command) {
        Objects.requireNonNull(command);
        accessGuard.requireWorkspace(command.principal(), REVOKE_PERMISSION, command.workspaceId(),
                AdenWorkspaceAccessGuard.OWNER_ONLY, command.correlationId().value());
        Instant now = clock.instant();
        long sequence = repository.lockWorkspaceEventSequence(command.workspaceId());
        var runner = repository.findRunnerForUpdate(command.workspaceId(), command.runnerId())
                .orElseThrow(AdenNotFoundException::new);
        repository.revokeRunner(runner, now);
        repository.revokeActiveCredentials(command.workspaceId(), command.runnerId(), now);
        repository.revokeActiveSessions(command.workspaceId(), command.runnerId(), now);
        var updated = new AdenRunnerLedgerRepository.RunnerFact(
                runner.workspaceId(), runner.runnerId(), runner.displayName(), "QUARANTINED",
                runner.capabilities(), runner.currentSessionEpoch(), runner.currentCredentialEpoch(),
                runner.version() + 1, runner.createdByUserId(), runner.createdAt(), now);
        appendEvent(sequence, updated, "aden.runner.status-changed.v1", "OPERATOR",
                Long.toString(command.principal().userId()), command.correlationId().value(), now,
                Map.of("from", runner.presence(), "to", "QUARANTINED",
                        "runnerId", runner.runnerId().value()));
        repository.insertAudit(new AdenRunnerLedgerRepository.RunnerAudit(
                command.workspaceId(), ids.nextId(), "RUNNER_REVOKED", command.runnerId(), "OPERATOR",
                Long.toString(command.principal().userId()), command.correlationId().value(), "{}", now));
    }

    private void appendEvent(long sequence, AdenRunnerLedgerRepository.RunnerFact runner,
                             String eventType, String actorType, String actorId,
                             String correlationId, Instant now, Object payload) {
        long next = Math.addExact(sequence, 1);
        repository.advanceWorkspaceEventSequence(runner.workspaceId(), sequence, next, now);
        String eventId = ids.nextId();
        repository.appendRunnerEvent(new AdenRunnerLedgerRepository.RunnerEvent(
                runner.workspaceId(), eventId, next, runner.runnerId(), runner.version(), eventType,
                json.canonicalJson(payload), correlationId, actorType, actorId, now));
        repository.insertOutbox(runner.workspaceId(), ids.nextId(), eventId, now);
    }

    private static String normalizeName(String value) {
        if (value == null) throw new IllegalArgumentException("displayName 不能为空");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 80
                || normalized.contains("\r") || normalized.contains("\n")) {
            throw new IllegalArgumentException("displayName 必须为 1..80 个非换行字符");
        }
        return normalized;
    }

    public record Enroll(AdenOperatorPrincipal principal, AdenWorkspaceId workspaceId,
                         String displayName, Set<AdenCapabilityCode> capabilities,
                         AdenCorrelationId correlationId) {
        public Enroll { capabilities = Set.copyOf(Objects.requireNonNull(capabilities)); }
    }

    public record Revoke(AdenOperatorPrincipal principal, AdenWorkspaceId workspaceId,
                         AdenRunnerId runnerId, AdenCorrelationId correlationId) { }

    /** credentialToken 是一次性敏感返回值，调用方必须设置 no-store 且禁止日志输出。 */
    public record Enrollment(AdenWorkspaceId workspaceId, AdenRunnerId runnerId,
                             AdenCredentialId credentialId, String credentialToken,
                             long credentialEpoch, Instant expiresAt, Instant issuedAt) { }
}
