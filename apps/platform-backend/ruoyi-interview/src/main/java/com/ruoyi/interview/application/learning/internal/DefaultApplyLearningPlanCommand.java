package com.ruoyi.interview.application.learning.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.learning.ApplyLearningPlanCommand;
import com.ruoyi.interview.application.learning.LearningPlanRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.learning.LearningPlan;

import java.util.Map;

/** confirm/cancel 的 owner-scoped、乐观锁与幂等入口。 */
public final class DefaultApplyLearningPlanCommand implements ApplyLearningPlanCommand {

    private static final String OPERATION = "learning.plan.command";

    private final LearningPlanRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultApplyLearningPlanCommand(LearningPlanRepository repository, ActivePrincipalGuard principal,
                                           IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                           TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            var decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    OPERATION, command.requestHash(), command.context()));
            LearningPlan plan = ownedPlan(owner, command.planId());
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                requireReplay(decision.resourceReferences(), plan);
                return new Result(plan);
            }
            requireNew(decision);
            switch (command.type()) {
                case CONFIRM -> plan.confirm(command.expectedVersion(), command.context().eventContext());
                case CANCEL -> plan.cancel(command.expectedVersion(), command.context().eventContext());
            }
            repository.save(plan);
            domainEvents.append(plan.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(OPERATION, command.requestHash(),
                    references(plan), 200, command.context()));
            return new Result(plan);
        });
    }

    private LearningPlan ownedPlan(com.ruoyi.interview.domain.platform.PrincipalRef owner,
                                   com.ruoyi.interview.domain.platform.ResourceId planId) {
        LearningPlan plan = repository.find(owner.tenantId(), planId).orElseThrow(
                DefaultApplyLearningPlanCommand::notFound);
        if (!plan.userId().equals(owner.userId())) {
            throw notFound();
        }
        return plan;
    }

    private static Map<String, String> references(LearningPlan plan) {
        return Map.of("learningPlanId", plan.id().value(),
                "learningPlanVersion", Long.toString(plan.version().value()));
    }

    private static void requireReplay(Map<String, String> references, LearningPlan plan) {
        String id = references.get("learningPlanId");
        String version = references.get("learningPlanVersion");
        if (!plan.id().value().equals(id) || version == null
                || !Long.toString(plan.version().value()).equals(version)) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "learning plan replay cannot reconstruct the original response", false,
                    Map.of("reasonCode", "IDEMPOTENT_RESPONSE_SNAPSHOT_UNAVAILABLE"));
        }
    }

    private static void requireNew(IdempotencyGuard.Decision decision) {
        if (decision.type() != IdempotencyGuard.DecisionType.NEW) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "idempotency guard returned a non-executable decision", false, Map.of());
        }
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "learning plan was not found", false, Map.of());
    }
}
