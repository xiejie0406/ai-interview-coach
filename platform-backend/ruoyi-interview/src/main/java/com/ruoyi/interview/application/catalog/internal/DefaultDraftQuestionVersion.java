package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.catalog.DraftQuestionVersion;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.catalog.port.VerifiedContentSourcePort;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.catalog.ContentSourceReference;
import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Map;

/** 已有题目的版本写入用例；所有正文仍只在应用/持久化边界内流转。 */
public final class DefaultDraftQuestionVersion implements DraftQuestionVersion {

    private final CatalogRepository repository;
    private final VerifiedContentSourcePort contentSources;
    private final CatalogAccess access;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultDraftQuestionVersion(
            CatalogRepository repository,
            VerifiedContentSourcePort contentSources,
            CatalogAccess access,
            IdGeneratorPort idGenerator,
            IdempotencyGuard idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.contentSources = java.util.Objects.requireNonNull(contentSources);
        this.access = java.util.Objects.requireNonNull(access);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(TenantId catalogTenantId, Command command) {
        TenantId targetTenant = access.requireCatalogTenant(catalogTenantId);
        return transaction.required(() -> {
            access.requireEditor(targetTenant, command.context());
            var principal = command.context().requirePrincipal();
            String sourceRef = command.contentSourceVersionId().map(Object::toString).orElse("");
            String requestHash = ServerSideDigest.sha256("catalog.question.version", targetTenant.value(),
                    command.questionId().value(),
                    command.category(),
                    command.title(), command.stem(), command.answerPoints().toString(),
                    command.misconceptions().toString(), command.followUpTemplates().toString(),
                    command.difficulty(), command.targetRoles().toString(), command.locale(), sourceRef,
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.question.version", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "question version request is already processing", true, Map.of());
            }
            var question = repository.findQuestion(targetTenant, command.questionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "question was not found", false,
                            Map.of("questionId", command.questionId().value())));
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(question.id(), com.ruoyi.interview.domain.platform.ResourceId.of(
                        decision.resourceReferences().get("questionVersionId")),
                        new com.ruoyi.interview.domain.platform.AggregateVersion(Long.parseLong(
                                decision.resourceReferences().getOrDefault("version", "0"))));
            }
            ContentSourceReference source = command.contentSourceVersionId()
                    .map(id -> contentSources.findVerified(targetTenant, id).orElseThrow(() ->
                            new DomainException(com.ruoyi.interview.domain.platform.DomainErrorCode.CONTENT_SOURCE_REQUIRED,
                                    "content source is not verified")))
                    .orElse(null);
            int versionNo = Math.max(question.currentDraftVersion().map(ref -> ref.versionNo()).orElse(0),
                    question.currentPublishedVersion().map(ref -> ref.versionNo()).orElse(0)) + 1;
            QuestionVersion version = new QuestionVersion(idGenerator.nextResourceId(), targetTenant,
                    question.id(), versionNo,
                    ServerSideDigest.sha256(command.title(), command.stem(), command.answerPoints().toString(),
                            command.misconceptions().toString(), command.followUpTemplates().toString(),
                            command.difficulty(), command.targetRoles().toString(), command.category(), command.locale()),
                    command.category(),
                    command.title(), command.stem(), command.answerPoints(), command.misconceptions(),
                    command.followUpTemplates(), command.difficulty(), command.targetRoles(), command.locale(),
                    java.util.Optional.ofNullable(source), principal.userId(), java.util.Optional.empty(),
                    command.context().requestedAt());
            repository.appendQuestionVersion(version);
            question.attachDraft(version, command.expectedVersion(), command.context().eventContext());
            repository.saveQuestion(question);
            domainEvents.append(question.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "catalog.question.version", requestHash,
                    Map.of("questionId", question.id().value(), "questionVersionId", version.id().value(),
                            "version", Long.toString(question.version().value())), 201, command.context()));
            return new Result(question.id(), version.id(), question.version());
        });
    }
}
