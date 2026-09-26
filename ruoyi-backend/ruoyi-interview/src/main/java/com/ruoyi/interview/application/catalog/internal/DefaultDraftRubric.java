package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.catalog.DraftRubric;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Map;

/** Rubric 版本只能绑定到同租户题目版本；绑定动作推进 Question 聚合版本并受 ETag 保护。 */
public final class DefaultDraftRubric implements DraftRubric {

    private final CatalogRepository repository;
    private final CatalogAccess access;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultDraftRubric(CatalogRepository repository,
                              CatalogAccess access,
                              IdGeneratorPort idGenerator,
                              IdempotencyGuard idempotency,
                              DomainEventPort domainEvents,
                              TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
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
            var question = repository.findQuestion(targetTenant, command.questionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "question was not found", false,
                            Map.of("questionId", command.questionId().value())));
            var questionVersion = repository.findQuestionVersion(targetTenant, command.questionVersionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "question version was not found", false, Map.of()));
            if (!questionVersion.questionId().equals(command.questionId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "question version was not found", false,
                        Map.of("questionVersionId", command.questionVersionId().value()));
            }
            String requestHash = ServerSideDigest.sha256("catalog.rubric.draft", targetTenant.value(),
                    command.questionId().value(),
                    command.questionVersionId().value(),
                    command.dimensions().toString(), command.refusalPolicy(),
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.rubric.draft", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "rubric draft request is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(com.ruoyi.interview.domain.platform.ResourceId.of(
                        decision.resourceReferences().get("rubricVersionId")), Integer.parseInt(
                        decision.resourceReferences().getOrDefault("versionNo", "1")));
            }
            int versionNo = repository.findLatestRubricForQuestionVersion(targetTenant,
                    questionVersion.id()).map(RubricVersion::versionNo).orElse(0) + 1;
            RubricVersion rubric = new RubricVersion(idGenerator.nextResourceId(), targetTenant,
                    questionVersion.id(), versionNo,
                    ServerSideDigest.sha256(command.dimensions().toString(), command.refusalPolicy()),
                    command.dimensions(), command.refusalPolicy(), command.context().requestedAt());
            // 绑定先推进 Question；随后 append + save + outbox 在同一事务内完成。
            question.attachRubric(rubric, command.expectedVersion(), command.context().eventContext());
            repository.appendRubricVersion(rubric);
            repository.saveQuestion(question);
            domainEvents.append(question.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "catalog.rubric.draft", requestHash, Map.of("rubricVersionId", rubric.id().value(),
                            "versionNo", Integer.toString(rubric.versionNo())),
                    201, command.context()));
            return new Result(rubric.id(), rubric.versionNo());
        });
    }
}
