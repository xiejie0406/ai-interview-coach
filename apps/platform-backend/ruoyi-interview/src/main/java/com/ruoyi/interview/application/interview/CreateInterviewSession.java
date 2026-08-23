package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

/** 同一 confirmed plan/version 只能创建一个活跃 Session。 */
@FunctionalInterface
public interface CreateInterviewSession {

    InterviewSessionSnapshot handle(Command command);

    record Command(ResourceId confirmedPlanId, int planVersionNo, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(confirmedPlanId, "confirmedPlanId");
            DomainPreconditions.require(planVersionNo > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "plan version number must be positive");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }
}
