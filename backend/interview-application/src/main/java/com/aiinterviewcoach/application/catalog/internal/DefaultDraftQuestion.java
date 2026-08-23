package com.aiinterviewcoach.application.catalog.internal;

import com.aiinterviewcoach.application.catalog.DraftQuestion;
import com.aiinterviewcoach.application.catalog.port.CatalogRepository;
import com.aiinterviewcoach.application.catalog.port.VerifiedContentSourcePort;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.catalog.ContentSourceReference;
import com.aiinterviewcoach.domain.catalog.Question;
import com.aiinterviewcoach.domain.catalog.QuestionVersion;
import com.aiinterviewcoach.domain.platform.DomainException;

import java.util.Map;

/** 创建不可变题目版本并移动 Question 草稿指针；正文只在聚合/持久化边界内流转。 */
public final class DefaultDraftQuestion implements DraftQuestion {

    private final CatalogRepository repository;
    private final VerifiedContentSourcePort contentSources;
    private final CatalogAccess access;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultDraftQuestion(CatalogRepository repository,
                                VerifiedContentSourcePort contentSources,
                                CatalogAccess access,
                                IdGeneratorPort idGenerator,
                                IdempotencyGuard idempotency,
                                DomainEventPort domainEvents,
                                TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.contentSources = java.util.Objects.requireNonNull(contentSources);
        this.access = java.util.Objects.requireNonNull(access);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            access.requireEditor(command.context());
            var principal = command.context().requirePrincipal();
            String sourceRef = command.contentSourceVersionId().map(Object::toString).orElse("");
            String requestHash = ServerSideDigest.sha256("catalog.question.draft", command.stableKey(),
                    command.title(), command.stem(), command.answerPoints().toString(), command.misconceptions().toString(),
                    command.followUpTemplates().toString(), command.difficulty(), command.targetRoles().toString(),
                    command.locale(), sourceRef);
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.question.draft", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "question draft request is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(com.aiinterviewcoach.domain.platform.ResourceId.of(
                        decision.resourceReferences().get("questionId")),
                        com.aiinterviewcoach.domain.platform.ResourceId.of(
                                decision.resourceReferences().get("questionVersionId")),
                        new com.aiinterviewcoach.domain.platform.AggregateVersion(Long.parseLong(
                                decision.resourceReferences().getOrDefault("version", "0"))));
            }
            ContentSourceReference source = command.contentSourceVersionId()
                    .map(id -> contentSources.findVerified(principal.tenantId(), id).orElseThrow(() ->
                            new DomainException(com.aiinterviewcoach.domain.platform.DomainErrorCode.CONTENT_SOURCE_REQUIRED,
                                    "content source is not verified")))
                    .orElse(null);
            Question question = repository.findByStableKey(principal.tenantId(), command.stableKey())
                    .orElseGet(() -> Question.draft(idGenerator.nextResourceId(), principal.tenantId(),
                            command.stableKey(), command.context().eventContext()));
            int versionNo = Math.max(question.currentDraftVersion().map(ref -> ref.versionNo()).orElse(0),
                    question.currentPublishedVersion().map(ref -> ref.versionNo()).orElse(0)) + 1;
            QuestionVersion version = new QuestionVersion(idGenerator.nextResourceId(), principal.tenantId(),
                    question.id(), versionNo,
                    ServerSideDigest.sha256(command.title(), command.stem(), command.answerPoints().toString(),
                            command.misconceptions().toString(), command.followUpTemplates().toString(),
                            command.difficulty(), command.targetRoles().toString(), command.locale()),
                    command.title(), command.stem(), command.answerPoints(), command.misconceptions(),
                    command.followUpTemplates(), command.difficulty(), command.targetRoles(), command.locale(),
                    java.util.Optional.ofNullable(source), principal.userId(), java.util.Optional.empty(),
                    command.context().requestedAt());
            repository.appendQuestionVersion(version);
            question.attachDraft(version, question.version(), command.context().eventContext());
            repository.saveQuestion(question);
            domainEvents.append(question.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "catalog.question.draft", requestHash,
                    Map.of("questionId", question.id().value(), "questionVersionId", version.id().value(),
                            "version", Long.toString(question.version().value())), 201, command.context()));
            return new Result(question.id(), version.id(), question.version());
        });
    }
}
