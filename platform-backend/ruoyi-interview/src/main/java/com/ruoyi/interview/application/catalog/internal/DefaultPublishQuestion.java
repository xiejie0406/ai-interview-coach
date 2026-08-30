package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.catalog.PublishQuestion;
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
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Map;

/** 审核发布只推进题目聚合，并追加不可变 publication 证据。 */
public final class DefaultPublishQuestion implements PublishQuestion {

    private final CatalogRepository repository;
    private final VerifiedContentSourcePort contentSources;
    private final CatalogAccess access;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultPublishQuestion(CatalogRepository repository, VerifiedContentSourcePort contentSources,
                                  CatalogAccess access, IdGeneratorPort idGenerator,
                                  IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                  TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.contentSources = java.util.Objects.requireNonNull(contentSources);
        this.access = java.util.Objects.requireNonNull(access);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    /**
     * 保留旧构造签名以避免下游编译断裂；没有来源 registry 时显式 fail-closed，绝不允许发布。
     */
    public DefaultPublishQuestion(CatalogRepository repository, CatalogAccess access, IdGeneratorPort idGenerator,
                                  IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                  TransactionPort transaction) {
        this(repository, (tenantId, sourceVersionId) -> java.util.Optional.empty(), access, idGenerator,
                idempotency, domainEvents, transaction);
    }

    @Override
    public Result handle(TenantId catalogTenantId, Command command) {
        TenantId targetTenant = access.requireCatalogTenant(catalogTenantId);
        return transaction.required(() -> {
            access.requireReviewer(targetTenant, command.context());
            var principal = command.context().requirePrincipal();
            String requestHash = ServerSideDigest.sha256("catalog.question.publish", targetTenant.value(),
                    command.questionId().value(),
                    command.questionVersionId().value(), command.rubricVersionId().value(),
                    Long.toString(command.expectedVersion().value()), command.reviewReasonCode());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.question.publish", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "question publication is already processing", true, Map.of());
            }
            var question = repository.findQuestion(targetTenant, command.questionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "question was not found", false, Map.of()));
            var questionVersion = repository.findQuestionVersion(targetTenant, command.questionVersionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "question version was not found", false, Map.of()));
            var rubric = repository.findRubricVersion(targetTenant, command.rubricVersionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "rubric version was not found", false, Map.of()));
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(com.ruoyi.interview.domain.platform.ResourceId.of(
                        decision.resourceReferences().get("publicationId")),
                        CatalogViews.snapshot(questionVersion, rubric),
                        new com.ruoyi.interview.domain.platform.AggregateVersion(Long.parseLong(
                                decision.resourceReferences().getOrDefault("version", "0"))));
            }
            assertVerifiedSource(targetTenant, questionVersion);
            var publication = question.publish(idGenerator.nextResourceId(), questionVersion, rubric,
                    principal.userId(), command.reviewReasonCode(), new com.ruoyi.interview.domain.catalog.PublicationPolicy(),
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

    /**
     * 发布是不可逆的公共内容边界；再次从服务端 registry 读取来源并逐字段比对，防止草稿期间
     * 来源被撤回/替换，或持久化的客户端快照伪造 VERIFIED 事实。
     */
    private void assertVerifiedSource(TenantId targetTenant, QuestionVersion questionVersion) {
        ContentSourceReference snapshot = questionVersion.source().orElseThrow(() ->
                new DomainException(DomainErrorCode.CONTENT_SOURCE_REQUIRED,
                        "question version requires a verified content source"));
        ContentSourceReference verified = contentSources.findVerified(
                        targetTenant, snapshot.sourceVersion().resourceId())
                .orElseThrow(() -> new DomainException(DomainErrorCode.CONTENT_SOURCE_REQUIRED,
                        "content source is not currently verified"));
        boolean matches = snapshot.sourceId().equals(verified.sourceId())
                && snapshot.sourceVersion().equals(verified.sourceVersion())
                && snapshot.licenseCode().equals(verified.licenseCode())
                && snapshot.verificationFactId().equals(verified.verificationFactId())
                && snapshot.verifiedAt().equals(verified.verifiedAt());
        if (!matches) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                    "question content source snapshot no longer matches the verified registry");
        }
    }
}
