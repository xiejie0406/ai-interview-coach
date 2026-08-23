package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.learning.LearningItemCommand;
import com.aiinterviewcoach.domain.learning.LearningPlan;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Optional;

@FunctionalInterface
public interface ApplyLearningItemCommand {
    Result handle(Command command);

    record Command(ResourceId itemId, LearningItemCommand type, Optional<Instant> scheduledAt,
                   Optional<String> reasonCode, AggregateVersion expectedItemVersion,
                   String requestHash, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(itemId, "learningItemId");
            DomainPreconditions.requireNonNull(type, "learningItemCommand");
            scheduledAt = scheduledAt == null ? Optional.empty() : scheduledAt;
            reasonCode = reasonCode == null ? Optional.empty() : reasonCode;
            reasonCode = reasonCode.map(value -> DomainPreconditions.requireText(value, "reasonCode"));
            reasonCode.ifPresent(value -> DomainPreconditions.require(value.length() <= 96,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "reasonCode is too long"));
            DomainPreconditions.requireNonNull(expectedItemVersion, "expectedItemVersion");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[itemId=" + itemId + ", type=" + type + ", scheduledAt=" + scheduledAt
                    + ", reasonCode=" + (reasonCode.isPresent() ? "<present>" : "<absent>")
                    + ", expectedItemVersion=" + expectedItemVersion + ", requestHash=<redacted>"
                    + ", context=" + context + "]";
        }
    }

    record Result(LearningPlan plan) {
        public Result { DomainPreconditions.requireNonNull(plan, "learningPlan"); }
    }
}
