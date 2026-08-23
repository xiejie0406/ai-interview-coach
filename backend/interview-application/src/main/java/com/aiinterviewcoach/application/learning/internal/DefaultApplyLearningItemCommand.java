package com.aiinterviewcoach.application.learning.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.learning.ApplyLearningItemCommand;
import com.aiinterviewcoach.application.learning.LearningPlanRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.learning.LearningPlan;

import java.util.Map;

/** item complete/skip/reschedule 的 owner-scoped、item-version 与幂等入口。 */
public final class DefaultApplyLearningItemCommand implements ApplyLearningItemCommand {

    private static final String OPERATION = "learning.item.command";

    private final LearningPlanRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultApplyLearningItemCommand(LearningPlanRepository repository, ActivePrincipalGuard principal,
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
            LearningPlan plan = repository.findByItem(owner.tenantId(), command.itemId())
                    .orElseThrow(DefaultApplyLearningItemCommand::notFound);
            if (!plan.userId().equals(owner.userId())) {
                throw notFound();
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                requireReplay(decision.resourceReferences(), plan, command.itemId());
                return new Result(plan);
            }
            requireNew(decision);
            plan.applyItemCommand(command.itemId(), command.type(), command.scheduledAt(), command.reasonCode(),
                    command.expectedItemVersion(), plan.version(), command.context().eventContext());
            repository.save(plan);
            domainEvents.append(plan.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(OPERATION, command.requestHash(),
                    references(plan, command.itemId()), 200, command.context()));
            return new Result(plan);
        });
    }

    private static Map<String, String> references(
            LearningPlan plan, com.aiinterviewcoach.domain.platform.ResourceId itemId) {
        var item = plan.items().stream().filter(value -> value.itemId().equals(itemId)).findFirst()
                .orElseThrow(DefaultApplyLearningItemCommand::notFound);
        return Map.of("learningPlanId", plan.id().value(),
                "learningPlanVersion", Long.toString(plan.version().value()),
                "learningItemId", item.itemId().value(),
                "learningItemVersion", Long.toString(item.version().value()));
    }

    private static void requireReplay(Map<String, String> references, LearningPlan plan,
                                      com.aiinterviewcoach.domain.platform.ResourceId itemId) {
        Map<String, String> current = references(plan, itemId);
        if (!current.equals(references)) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "learning item replay cannot reconstruct the original response", false,
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
                "learning item was not found", false, Map.of());
    }
}
