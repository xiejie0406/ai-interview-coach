package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.ApplySessionCommand;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.interview.SessionCommandType;

import java.util.Map;

/** Session 控制命令唯一入口；状态迁移由 InterviewSession 聚合裁决。 */
public final class DefaultApplySessionCommand implements ApplySessionCommand {

    private final InterviewRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;
    private final InterviewSnapshotFactory snapshots;

    public DefaultApplySessionCommand(InterviewRepository repository, ActivePrincipalGuard principal,
                                      IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                      TransactionPort transaction,
                                      InterviewRecoveryProjectionPort projections) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        this.snapshots = new InterviewSnapshotFactory(projections);
    }

    @Override
    public com.ruoyi.interview.application.interview.InterviewSessionSnapshot handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("interview.session.command", command.sessionId().value(),
                    command.commandType().name(), command.reasonCode().orElse(""),
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "interview.session.command", requestHash, command.context()));
            var session = repository.findSession(owner.tenantId(), command.sessionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview session was not found", false, Map.of()));
            if (!session.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview session was not found", false, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "session command is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return snapshots.create(session, owner);
            }
            switch (command.commandType()) {
                case PAUSE -> session.pause(command.expectedVersion(), command.context().eventContext());
                case RESUME -> session.resume(command.expectedVersion(), command.context().eventContext());
                case SKIP -> session.skipCurrentTurn(command.expectedVersion(), command.context().eventContext());
                case COMPLETE -> session.beginCompletion(command.expectedVersion(), command.context().eventContext());
                case CANCEL -> session.cancel(command.expectedVersion(), command.context().eventContext());
                case RECOVER -> session.recover(command.expectedVersion(), command.context().eventContext());
                default -> throw new com.ruoyi.interview.domain.platform.DomainException(
                        com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                        "command must use a dedicated session use case");
            }
            repository.saveSession(session);
            domainEvents.append(session.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("interview.session.command", requestHash,
                    Map.of("sessionId", session.id().value()), 200, command.context()));
            return snapshots.create(session, owner);
        });
    }
}
