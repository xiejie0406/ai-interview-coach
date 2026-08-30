package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.application.platform.JobExecutionContext;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.evaluation.EvaluationStage;
import com.ruoyi.interview.domain.evaluation.EvaluationStatus;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;

/** Worker-facing pipeline；每次业务回写都必须重验 Job lease/attempt/version。 */
@FunctionalInterface
public interface RunEvaluationPipeline {

    Result handle(Command command);

    record Command(ResourceId evaluationId, JobExecutionContext execution, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(execution, "jobExecutionContext");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requireServiceActor();
            DomainPreconditions.require(execution.tenantId().equals(context.requireTenantScope()),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.OWNERSHIP_DENIED,
                    "job execution tenant does not match actor tenant");
        }
    }

    record Result(ResourceId evaluationId, EvaluationStatus state, EvaluationStage stage,
                  Optional<ResourceId> reportId) {
        public Result {
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(state, "evaluationState");
            DomainPreconditions.requireNonNull(stage, "evaluationStage");
            reportId = reportId == null ? Optional.empty() : reportId;
        }
    }
}
