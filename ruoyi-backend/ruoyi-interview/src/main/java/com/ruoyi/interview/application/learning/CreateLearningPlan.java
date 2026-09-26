package com.ruoyi.interview.application.learning;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.learning.LearningPlan;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

/** POST /reports/{reportId}/learning-plans；调用者不能提交模型 items/goals。 */
@FunctionalInterface
public interface CreateLearningPlan {

    Result handle(Command command);

    record Command(ResourceId sourceReportId, String requestHash, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(sourceReportId, "sourceReportId");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[sourceReportId=" + sourceReportId + ", requestHash=<redacted>"
                    + ", context=" + context + "]";
        }
    }

    record Result(LearningPlan plan) {
        public Result {
            DomainPreconditions.requireNonNull(plan, "learningPlan");
        }
    }
}
