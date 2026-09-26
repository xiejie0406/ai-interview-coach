package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.catalog.RetireQuestion;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Map;

public final class DefaultRetireQuestion implements RetireQuestion {

    private final CatalogRepository repository;
    private final CatalogAccess access;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultRetireQuestion(CatalogRepository repository, CatalogAccess access,
                                 IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                 TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.access = java.util.Objects.requireNonNull(access);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(TenantId catalogTenantId, Command command) {
        TenantId targetTenant = access.requireCatalogTenant(catalogTenantId);
        return transaction.required(() -> {
            access.requireReviewer(targetTenant, command.context());
            var principal = command.context().requirePrincipal();
            String requestHash = ServerSideDigest.sha256("catalog.question.retire", targetTenant.value(),
                    command.questionId().value(),
                    command.reasonCode(), Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "catalog.question.retire", requestHash, command.context()));
            var question = repository.findQuestion(targetTenant, command.questionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "question was not found", false, Map.of()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "question retirement is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(question.id(), new com.ruoyi.interview.domain.platform.AggregateVersion(Long.parseLong(
                        decision.resourceReferences().getOrDefault("version", "0"))));
            }
            question.retire(command.expectedVersion(), command.context().eventContext());
            repository.saveQuestion(question);
            domainEvents.append(question.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("catalog.question.retire", requestHash,
                    Map.of("questionId", question.id().value(), "version", Long.toString(question.version().value())),
                    200, command.context()));
            return new Result(question.id(), question.version());
        });
    }
}
