package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** 已确认计划取消时，Plan 与未用 Reservation 释放必须在同一应用事务协调。 */
@FunctionalInterface
public interface CancelInterviewPlan {

    InterviewPlanView handle(Command command);

    record Command(
            ResourceId planId,
            String acknowledgedEstimateVersion,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(planId, "planId");
            acknowledgedEstimateVersion = DomainPreconditions.requireText(
                    acknowledgedEstimateVersion, "acknowledgedEstimateVersion");
            DomainPreconditions.require(acknowledgedEstimateVersion.length() <= 128,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "acknowledgedEstimateVersion is too long");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[planId=" + planId + ", acknowledgedEstimateVersion="
                    + acknowledgedEstimateVersion + ", expectedVersion=" + expectedVersion
                    + ", context=" + context + "]";
        }
    }
}
