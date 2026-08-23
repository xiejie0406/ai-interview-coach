package com.aiinterviewcoach.application.catalog.internal;

import com.aiinterviewcoach.application.catalog.RejectQuestionReview;
import com.aiinterviewcoach.application.catalog.port.CatalogRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

public final class DefaultRejectQuestionReview implements RejectQuestionReview {

    private final CatalogRepository repository;
    private final CatalogAccess access;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultRejectQuestionReview(CatalogRepository repository, CatalogAccess access,
                                       IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                       TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.access = java.util.Objects.requireNonNull(access);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            access.requireReviewer(command.context());
            var principal = command.context().requirePrincipal();
            String requestHash = ServerSideDigest.sha256("catalog.question.reject", command.questionId().value(),
                    command.reasonCode(), Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.question.reject", requestHash, command.context()));
            var question = repository.findQuestion(principal.tenantId(), command.questionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "question was not found", false, Map.of()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "question rejection is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(question.id(), new com.aiinterviewcoach.domain.platform.AggregateVersion(Long.parseLong(
                        decision.resourceReferences().getOrDefault("version", "0"))));
            }
            question.rejectReview(command.reasonCode(), principal.userId(), command.expectedVersion(),
                    command.context().eventContext());
            repository.saveQuestion(question);
            domainEvents.append(question.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("catalog.question.reject", requestHash,
                    Map.of("questionId", question.id().value(), "version", Long.toString(question.version().value())),
                    200, command.context()));
            return new Result(question.id(), question.version());
        });
    }
}
