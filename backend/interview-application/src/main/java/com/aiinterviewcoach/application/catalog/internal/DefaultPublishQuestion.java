package com.aiinterviewcoach.application.catalog.internal;

import com.aiinterviewcoach.application.catalog.PublishQuestion;
import com.aiinterviewcoach.application.catalog.port.CatalogRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** 审核发布只推进题目聚合，并追加不可变 publication 证据。 */
public final class DefaultPublishQuestion implements PublishQuestion {

    private final CatalogRepository repository;
    private final CatalogAccess access;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultPublishQuestion(CatalogRepository repository, CatalogAccess access, IdGeneratorPort idGenerator,
                                  IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                  TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.access = java.util.Objects.requireNonNull(access);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            access.requireReviewer(command.context());
            var principal = command.context().requirePrincipal();
            String requestHash = ServerSideDigest.sha256("catalog.question.publish", command.questionId().value(),
                    command.questionVersionId().value(), command.rubricVersionId().value(),
                    Long.toString(command.expectedVersion().value()), command.reviewReasonCode());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.question.publish", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "question publication is already processing", true, Map.of());
            }
            var question = repository.findQuestion(principal.tenantId(), command.questionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "question was not found", false, Map.of()));
            var questionVersion = repository.findQuestionVersion(principal.tenantId(), command.questionVersionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "question version was not found", false, Map.of()));
            var rubric = repository.findRubricVersion(principal.tenantId(), command.rubricVersionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "rubric version was not found", false, Map.of()));
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(com.aiinterviewcoach.domain.platform.ResourceId.of(
                        decision.resourceReferences().get("publicationId")),
                        CatalogViews.snapshot(questionVersion, rubric),
                        new com.aiinterviewcoach.domain.platform.AggregateVersion(Long.parseLong(
                                decision.resourceReferences().getOrDefault("version", "0"))));
            }
            var publication = question.publish(idGenerator.nextResourceId(), questionVersion, rubric,
                    principal.userId(), command.reviewReasonCode(), new com.aiinterviewcoach.domain.catalog.PublicationPolicy(),
                    command.expectedVersion(), command.context().eventContext());
            repository.saveQuestion(question);
            repository.appendPublication(publication);
            domainEvents.append(question.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "catalog.question.publish", requestHash,
                    Map.of("publicationId", publication.id().value(), "version", Long.toString(question.version().value())),
                    200, command.context()));
            return new Result(publication.id(), CatalogViews.snapshot(questionVersion, rubric), question.version());
        });
    }
}
