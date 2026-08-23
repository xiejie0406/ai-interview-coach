package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.catalog.DraftRubric;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.catalog.RubricVersion;

import java.util.Map;

/** Rubric 版本只能绑定到同租户题目版本；当前公共端口不提供旧 Rubric 列表，因此每题版本只接受一次初版。 */
public final class DefaultDraftRubric implements DraftRubric {

    private final CatalogRepository repository;
    private final CatalogAccess access;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final TransactionPort transaction;

    public DefaultDraftRubric(CatalogRepository repository,
                              CatalogAccess access,
                              IdGeneratorPort idGenerator,
                              IdempotencyGuard idempotency,
                              TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.access = java.util.Objects.requireNonNull(access);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            access.requireEditor(command.context());
            var principal = command.context().requirePrincipal();
            var questionVersion = repository.findQuestionVersion(principal.tenantId(), command.questionVersionId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "question version was not found", false, Map.of()));
            if (!questionVersion.authoredBy().equals(principal.userId())) {
                throw new ApplicationException(ApplicationErrorCode.FORBIDDEN,
                        "question version is owned by another author", false, Map.of());
            }
            String requestHash = ServerSideDigest.sha256("catalog.rubric.draft", command.questionVersionId().value(),
                    command.dimensions().toString(), command.refusalPolicy());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.rubric.draft", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "rubric draft request is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(com.ruoyi.interview.domain.platform.ResourceId.of(
                        decision.resourceReferences().get("rubricVersionId")), 1);
            }
            RubricVersion rubric = new RubricVersion(idGenerator.nextResourceId(), principal.tenantId(),
                    questionVersion.id(), 1,
                    ServerSideDigest.sha256(command.dimensions().toString(), command.refusalPolicy()),
                    command.dimensions(), command.refusalPolicy(), command.context().requestedAt());
            repository.appendRubricVersion(rubric);
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "catalog.rubric.draft", requestHash, Map.of("rubricVersionId", rubric.id().value()),
                    201, command.context()));
            return new Result(rubric.id(), rubric.versionNo());
        });
    }
}
