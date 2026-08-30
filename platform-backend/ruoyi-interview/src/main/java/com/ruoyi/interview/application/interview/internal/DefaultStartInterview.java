package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.billing.port.EntitlementPort;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.StartInterview;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.platform.Job;

import java.util.Map;

/** Session 开始与首个 Interview Step Job 在同一事务受理。 */
public final class DefaultStartInterview implements StartInterview {

    private final InterviewRepository repository;
    private final EntitlementPort billing;
    private final JobPort jobs;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;
    private final int maxAttempts;
    private final InterviewSnapshotFactory snapshots;

    public DefaultStartInterview(InterviewRepository repository, EntitlementPort billing, JobPort jobs, ActivePrincipalGuard principal,
                                 IdGeneratorPort idGenerator, IdempotencyGuard idempotency,
                                 DomainEventPort domainEvents, TransactionPort transaction,
                                 InterviewRecoveryProjectionPort projections) {
        this(repository, billing, jobs, principal, idGenerator, idempotency, domainEvents,
                transaction, projections, 3);
    }

    public DefaultStartInterview(InterviewRepository repository, EntitlementPort billing, JobPort jobs, ActivePrincipalGuard principal,
                                 IdGeneratorPort idGenerator, IdempotencyGuard idempotency,
                                 DomainEventPort domainEvents, TransactionPort transaction,
                                 InterviewRecoveryProjectionPort projections, int maxAttempts) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.billing = java.util.Objects.requireNonNull(billing);
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        this.snapshots = new InterviewSnapshotFactory(projections);
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("interview.start", command.sessionId().value(),
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "interview.start", requestHash, command.context()));
            var session = repository.findSession(owner.tenantId(), command.sessionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview session was not found", false, Map.of()));
            if (!session.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview session was not found", false, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "interview start is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(snapshots.create(session, owner),
                        OperationAcceptedFactory.replay(decision.resourceReferences()));
            }
            billing.requireActiveReservation(new EntitlementPort.ActiveReservationRequest(
                    owner.tenantId(), owner.userId(), session.planReference().planId(),
                    session.planReference().usageReservationId(), command.context().requestedAt()));
            session.start(command.expectedVersion(), command.context().eventContext());
            var job = Job.schedule(idGenerator.nextResourceId(), owner.tenantId(), "INTERVIEW_STEP", session.id(),
                    Map.of("sessionId", session.id().value()), maxAttempts, command.context().requestedAt(),
                    command.context().eventContext());
            repository.saveSession(session);
            jobs.save(job);
            domainEvents.append(session.pullDomainEvents());
            domainEvents.append(job.pullDomainEvents());
            var accepted = OperationAcceptedFactory.forJob(job.id(), session.id(), command.context().requestedAt());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("interview.start", requestHash,
                    OperationAcceptedFactory.idempotencyReferences(accepted, Map.of()), 202, command.context()));
            return new Result(snapshots.create(session, owner), accepted);
        });
    }
}
