package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

@FunctionalInterface
public interface RejectQuestionReview {

    Result handle(Command command);

    record Command(
            ResourceId questionId,
            String reasonCode,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            reasonCode = DomainPreconditions.requireText(reasonCode, "reasonCode");
            DomainPreconditions.require(reasonCode.matches("[A-Z][A-Z0-9_]{0,95}"),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "reasonCode is invalid");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[questionId=" + questionId + ", reasonCode=<redacted>"
                    + ", expectedVersion=" + expectedVersion + ", context=" + context + "]";
        }
    }

    record Result(ResourceId questionId, AggregateVersion version) {
        public Result {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            DomainPreconditions.requireNonNull(version, "questionVersion");
        }
    }
}
