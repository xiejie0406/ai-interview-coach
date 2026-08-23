package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.learning.LearningPlan;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

@FunctionalInterface
public interface ApplyLearningPlanCommand {
    Result handle(Command command);

    enum Type { CONFIRM, CANCEL }

    record Command(ResourceId planId, Type type, AggregateVersion expectedVersion,
                   String requestHash, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(planId, "learningPlanId");
            DomainPreconditions.requireNonNull(type, "learningPlanCommand");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedPlanVersion");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[planId=" + planId + ", type=" + type
                    + ", expectedVersion=" + expectedVersion + ", requestHash=<redacted>"
                    + ", context=" + context + "]";
        }
    }

    record Result(LearningPlan plan) {
        public Result { DomainPreconditions.requireNonNull(plan, "learningPlan"); }
    }
}
