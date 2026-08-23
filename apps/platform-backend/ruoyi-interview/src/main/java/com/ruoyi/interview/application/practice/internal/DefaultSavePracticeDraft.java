package com.ruoyi.interview.application.practice.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.practice.SavePracticeDraft;
import com.ruoyi.interview.application.practice.port.PracticeRepository;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

public final class DefaultSavePracticeDraft implements SavePracticeDraft {

    private final PracticeRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultSavePracticeDraft(PracticeRepository repository, ActivePrincipalGuard principal,
                                    IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                    TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public com.ruoyi.interview.application.practice.PracticeAttemptView handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("practice.draft", command.attemptId().value(),
                    command.answerText());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "practice.draft", requestHash, command.context()));
            var attempt = repository.find(owner.tenantId(), command.attemptId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "practice attempt was not found", false, Map.of()));
            if (!attempt.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "practice attempt was not found", false, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "practice draft is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return PracticeViews.view(attempt);
            }
            attempt.saveDraft(command.answerText(), ServerSideDigest.sha256(command.answerText()),
                    command.expectedVersion(), command.context().eventContext());
            repository.save(attempt);
            domainEvents.append(attempt.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("practice.draft", requestHash,
                    Map.of("attemptId", attempt.id().value(), "version", Long.toString(attempt.version().value())),
                    200, command.context()));
            return PracticeViews.view(attempt);
        });
    }
}
