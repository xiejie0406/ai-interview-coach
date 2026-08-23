package com.aiinterviewcoach.application.platform;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.JobState;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** 仅消费已持久化的 Provider 回读/补偿回执；此用例本身不得猜测外部 effect。 */
@FunctionalInterface
public interface ReconcileExpiredJobCancellation {

    Result handle(Command command);

    enum Outcome {
        CANCELLED_WITHOUT_EFFECT,
        SUCCEEDED_EFFECT_CONFIRMED
    }

    record Command(
            ResourceId jobId,
            ResourceId reconciliationReceiptId,
            Outcome outcome,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(jobId, "jobId");
            DomainPreconditions.requireNonNull(reconciliationReceiptId, "reconciliationReceiptId");
            DomainPreconditions.requireNonNull(outcome, "reconciliationOutcome");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requireServiceActor();
        }
    }

    record Result(ResourceId jobId, JobState state, AggregateVersion version) {
        public Result {
            DomainPreconditions.requireNonNull(jobId, "jobId");
            DomainPreconditions.requireNonNull(state, "jobState");
            DomainPreconditions.requireNonNull(version, "jobVersion");
        }
    }
}
