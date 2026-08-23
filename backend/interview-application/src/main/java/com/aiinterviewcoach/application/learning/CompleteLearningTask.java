package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.learning.LearningProgressSnapshot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

@FunctionalInterface
@Deprecated(forRemoval = false)
public interface CompleteLearningTask {

    Result handle(Command command);

    record Command(
            ResourceId learningPlanId,
            ResourceId taskId,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(learningPlanId, "learningPlanId");
            DomainPreconditions.requireNonNull(taskId, "taskId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }

    record Result(ResourceId learningPlanId, AggregateVersion version, LearningProgressSnapshot progress) {
        public Result {
            DomainPreconditions.requireNonNull(learningPlanId, "learningPlanId");
            DomainPreconditions.requireNonNull(version, "learningPlanVersion");
            DomainPreconditions.requireNonNull(progress, "learningProgress");
        }
    }
}
