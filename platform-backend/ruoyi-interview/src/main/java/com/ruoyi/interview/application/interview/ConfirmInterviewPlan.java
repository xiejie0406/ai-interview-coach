package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

/** 用户确认当前 estimate version；服务端在同一本地事务创建 Reservation 并确认计划。 */
@FunctionalInterface
public interface ConfirmInterviewPlan {

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
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
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
