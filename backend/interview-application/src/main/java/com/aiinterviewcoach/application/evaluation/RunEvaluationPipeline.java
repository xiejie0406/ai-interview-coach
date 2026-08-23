package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.application.platform.JobExecutionContext;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.evaluation.EvaluationStage;
import com.aiinterviewcoach.domain.evaluation.EvaluationStatus;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

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
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.OWNERSHIP_DENIED,
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
