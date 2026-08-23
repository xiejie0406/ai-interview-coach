package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** 同一 confirmed plan/version 只能创建一个活跃 Session。 */
@FunctionalInterface
public interface CreateInterviewSession {

    InterviewSessionSnapshot handle(Command command);

    record Command(ResourceId confirmedPlanId, int planVersionNo, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(confirmedPlanId, "confirmedPlanId");
            DomainPreconditions.require(planVersionNo > 0,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "plan version number must be positive");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }
}
