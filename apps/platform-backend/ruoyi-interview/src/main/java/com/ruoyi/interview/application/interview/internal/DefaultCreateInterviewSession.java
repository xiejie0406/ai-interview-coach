package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.billing.port.EntitlementPort;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.CreateInterviewSession;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.interview.InterviewPlanState;
import com.ruoyi.interview.domain.interview.InterviewSession;

import java.util.Map;

/** 同一确认计划版本只允许一个 Session。 */
public final class DefaultCreateInterviewSession implements CreateInterviewSession {

    private final InterviewRepository repository;
    private final EntitlementPort billing;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;
    private final InterviewSnapshotFactory snapshots;

    public DefaultCreateInterviewSession(InterviewRepository repository, EntitlementPort billing, ActivePrincipalGuard principal,
                                         IdGeneratorPort idGenerator, IdempotencyGuard idempotency,
                                         DomainEventPort domainEvents, TransactionPort transaction,
                                         InterviewRecoveryProjectionPort projections) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.billing = java.util.Objects.requireNonNull(billing);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        this.snapshots = new InterviewSnapshotFactory(projections);
    }

    @Override
    public com.ruoyi.interview.application.interview.InterviewSessionSnapshot handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("interview.session.create", command.confirmedPlanId().value(),
                    Integer.toString(command.planVersionNo()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "interview.session.create", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "interview session creation is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String sessionId = decision.resourceReferences().get("sessionId");
                if (sessionId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "session replay has no session reference", false, Map.of());
                }
                return repository.findSession(owner.tenantId(), com.ruoyi.interview.domain.platform.ResourceId.of(sessionId))
                        .map(session -> snapshots.create(session, owner))
                        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                                "session replay record is missing", false, Map.of()));
            }
            var plan = repository.findPlan(owner.tenantId(), command.confirmedPlanId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview plan was not found", false, Map.of()));
            if (!plan.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "interview plan was not found", false, Map.of());
            }
            if (plan.state() != InterviewPlanState.CONFIRMED
                    || plan.planVersionNo() != command.planVersionNo()) {
                throw new com.ruoyi.interview.domain.platform.DomainException(
                        com.ruoyi.interview.domain.platform.DomainErrorCode.POLICY_DENIED,
                        "interview plan is not the requested confirmed version");
            }
            billing.requireActiveReservation(new EntitlementPort.ActiveReservationRequest(
                    owner.tenantId(), owner.userId(), plan.id(), plan.usageReservationId().orElseThrow(),
                    command.context().requestedAt()));
            if (repository.findSessionByPlan(owner.tenantId(), plan.id(), plan.planVersionNo()).isPresent()) {
                throw new com.ruoyi.interview.domain.platform.DomainException(
                        com.ruoyi.interview.domain.platform.DomainErrorCode.SESSION_ALREADY_ACTIVE,
                        "a session already exists for this confirmed plan version");
            }
            var session = InterviewSession.ready(idGenerator.nextResourceId(), owner.tenantId(), owner.userId(),
                    plan.confirmedReference(), plan.mode(), command.context().eventContext());
            repository.saveSession(session);
            domainEvents.append(session.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("interview.session.create", requestHash,
                    Map.of("sessionId", session.id().value()), 201, command.context()));
            return snapshots.create(session, owner);
        });
    }
}
