package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** POST /answers/{answerVersionId}/evaluations 的 durable 受理边界。 */
@FunctionalInterface
public interface StartEvaluation {

    Result handle(Command command);

    record Command(ResourceId answerVersionId, String requestHash, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[answerVersionId=" + answerVersionId + ", requestHash=<redacted>"
                    + ", context=" + context + "]";
        }
    }

    record Result(OperationAccepted operation) {
        public Result {
            DomainPreconditions.requireNonNull(operation, "acceptedOperation");
        }
    }
}
