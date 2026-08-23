package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** Session、首题 Job、Idempotency 与 Outbox 同一本地事务受理。 */
@FunctionalInterface
public interface StartInterview {

    Result handle(Command command);

    record Command(ResourceId sessionId, AggregateVersion expectedVersion, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }

    record Result(InterviewSessionSnapshot snapshot, OperationAccepted nextStepOperation) {
        public Result {
            DomainPreconditions.requireNonNull(snapshot, "sessionSnapshot");
            DomainPreconditions.requireNonNull(nextStepOperation, "nextStepOperation");
        }
    }
}
