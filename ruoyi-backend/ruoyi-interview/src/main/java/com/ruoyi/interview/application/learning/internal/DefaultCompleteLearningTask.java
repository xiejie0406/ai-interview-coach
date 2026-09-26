package com.ruoyi.interview.application.learning.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.learning.CompleteLearningTask;
import com.ruoyi.interview.application.learning.LearningPlanRepository;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.learning.LearningItemCommand;

import java.util.Map;

/** Updates progress using the plan aggregate version; duplicate completion is rejected. */
@Deprecated(forRemoval = false)
public final class DefaultCompleteLearningTask implements CompleteLearningTask {

    private final LearningPlanRepository repository;
    private final ActivePrincipalGuard principal;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultCompleteLearningTask(
            LearningPlanRepository repository,
            ActivePrincipalGuard principal,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            var plan = repository.find(owner.tenantId(), command.learningPlanId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "learning plan was not found", false, Map.of()));
            if (!plan.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "learning plan was not found", false, Map.of());
            }
            var item = plan.items().stream().filter(value -> value.itemId().equals(command.taskId())).findFirst()
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "learning item was not found", false, Map.of()));
            plan.applyItemCommand(command.taskId(), LearningItemCommand.COMPLETE, java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    item.version(), command.expectedVersion(), command.context().eventContext());
            repository.save(plan);
            domainEvents.append(plan.pullDomainEvents());
            return new Result(plan.id(), plan.version(), plan.progressSnapshot());
        });
    }
}
