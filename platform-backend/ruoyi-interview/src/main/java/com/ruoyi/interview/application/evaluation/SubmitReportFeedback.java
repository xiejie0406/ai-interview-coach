package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.evaluation.FeedbackType;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;

/** Feedback endpoint 以 evaluationId 为入口；comment 是私密正文，必须交给受控 content port。 */
@FunctionalInterface
public interface SubmitReportFeedback {

    Result handle(Command command);

    record Command(ResourceId evaluationId, FeedbackType type, Optional<String> comment,
                   String requestHash, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(type, "feedbackType");
            comment = comment == null ? Optional.empty() : comment;
            comment = comment.map(value -> DomainPreconditions.requireText(value, "feedbackComment"));
            comment.ifPresent(value -> DomainPreconditions.require(value.length() <= 2000,
                    DomainErrorCode.INVALID_ARGUMENT, "feedback comment is too long"));
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[evaluationId=" + evaluationId + ", type=" + type
                    + ", comment=<redacted>, requestHash=<redacted>, context=" + context + "]";
        }
    }

    record Result(ResourceId feedbackId, ResourceId reportId,
                  com.ruoyi.interview.domain.platform.AggregateVersion reportVersion) {
        public Result {
            DomainPreconditions.requireNonNull(feedbackId, "feedbackId");
            DomainPreconditions.requireNonNull(reportId, "reportId");
            DomainPreconditions.requireNonNull(reportVersion, "reportVersion");
        }
    }
}
