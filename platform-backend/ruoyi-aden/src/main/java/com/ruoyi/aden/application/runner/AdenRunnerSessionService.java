package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.domain.runner.AdenRunnerSession;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.runner.AdenSessionStatus;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.infrastructure.security.AdenRunnerSessionCodec;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Session 换代事务严格按 Workspace→Runner→Credential→Session 执行。 */
public class AdenRunnerSessionService {
    private final AdenRunnerLedgerRepository repository;
    private final AdenRunnerSessionCodec sessionCodec;
    private final AdenRunnerPepperProvider pepperProvider;
    private final AdenRequestFingerprint json;
    private final AdenIdGenerator ids;
    private final Clock clock;
    private final Duration sessionTtl;

    public AdenRunnerSessionService(AdenRunnerLedgerRepository repository,
                                    AdenRunnerSessionCodec sessionCodec,
                                    AdenRunnerPepperProvider pepperProvider,
                                    AdenRequestFingerprint json,
                                    AdenIdGenerator ids, Clock clock, Duration sessionTtl) {
        this.repository = Objects.requireNonNull(repository);
        this.sessionCodec = Objects.requireNonNull(sessionCodec);
        this.pepperProvider = Objects.requireNonNull(pepperProvider);
        this.json = Objects.requireNonNull(json);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        this.sessionTtl = Objects.requireNonNull(sessionTtl);
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public SessionExchange exchange(AdenRunnerCredentialPrincipal principal, CreateSession request,
                                    String correlationId) {
        Objects.requireNonNull(principal);
        Objects.requireNonNull(request);
        Instant now = clock.instant();
        long sequence = repository.lockWorkspaceEventSequence(principal.workspaceId());
        var runner = repository.findRunnerForUpdate(principal.workspaceId(), principal.runnerId())
                .orElseThrow(this::invalidAuth);
        var credential = repository.findCredentialForUpdate(
                principal.workspaceId(), principal.credentialId()).orElseThrow(this::invalidAuth);
        if (!credential.runnerId().equals(runner.runnerId())
                || credential.epoch() != principal.credentialEpoch()
                || credential.epoch() != runner.currentCredentialEpoch()
                || !credential.activeAt(now) || "QUARANTINED".equals(runner.presence())) {
            throw invalidAuth();
        }
        if (request.protocolVersion() != 1 || request.capacity() < 1 || request.capacity() > 32
                || !runner.capabilities().containsAll(request.capabilities())) {
            throw new IllegalArgumentException("Runner Session 请求与注册能力不兼容");
        }
        long nextEpoch = Math.addExact(runner.currentSessionEpoch(), 1);
        repository.advanceRunnerForSession(runner, nextEpoch, now);
        repository.supersedeActiveSessions(principal.workspaceId(), principal.runnerId(), now);
        AdenSessionId sessionId = new AdenSessionId(ids.nextId());
        String pepperKeyId = pepperProvider.currentKeyId();
        var issued = sessionCodec.issue(sessionId, pepperKeyId, pepperProvider.pepper(pepperKeyId));
        AdenRunnerSession session = new AdenRunnerSession(
                principal.workspaceId(), sessionId, principal.runnerId(), principal.credentialId(),
                AdenSessionStatus.ACTIVE, nextEpoch, request.capacity(), 0, 0,
                now, now.plus(sessionTtl), 0);
        repository.insertSession(session, issued.keyedDigest(), issued.pepperKeyId(),
                json.canonicalJson(Map.of("protocolVersion", request.protocolVersion(),
                        "runnerVersion", request.runnerVersion(), "capabilities", request.capabilities())), now);
        long nextSequence = Math.addExact(sequence, 1);
        repository.advanceWorkspaceEventSequence(principal.workspaceId(), sequence, nextSequence, now);
        String eventId = ids.nextId();
        repository.appendRunnerEvent(new AdenRunnerLedgerRepository.RunnerEvent(
                principal.workspaceId(), eventId, nextSequence, principal.runnerId(), runner.version() + 1,
                "aden.runner.status-changed.v1", json.canonicalJson(Map.of(
                "to", "ONLINE", "runnerId", principal.runnerId().value())),
                correlationId, "RUNNER", principal.runnerId().value(), now));
        repository.insertOutbox(principal.workspaceId(), ids.nextId(), eventId, now);
        repository.insertAudit(new AdenRunnerLedgerRepository.RunnerAudit(
                principal.workspaceId(), ids.nextId(), "RUNNER_SESSION_EXCHANGED", principal.runnerId(),
                "RUNNER", principal.runnerId().value(), correlationId,
                json.canonicalJson(Map.of("sessionEpoch", Long.toString(nextEpoch))), now));
        return new SessionExchange(principal.workspaceId(), principal.runnerId(), sessionId,
                issued.bearer(), nextEpoch, session.expiresAt(), now);
    }

    private AdenApplicationException invalidAuth() {
        return new AdenApplicationException("ADEN_RUNNER_AUTH_INVALID", "Runner 凭据或会话无效");
    }

    public record CreateSession(int protocolVersion, String runnerVersion,
                                int capacity, Set<AdenCapabilityCode> capabilities) {
        public CreateSession {
            Objects.requireNonNull(runnerVersion);
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities));
        }
    }

    public record SessionExchange(com.ruoyi.aden.domain.workspace.AdenWorkspaceId workspaceId,
                                  com.ruoyi.aden.domain.runner.AdenRunnerId runnerId,
                                  AdenSessionId sessionId, String sessionToken,
                                  long sessionEpoch, Instant expiresAt, Instant issuedAt) { }
}
