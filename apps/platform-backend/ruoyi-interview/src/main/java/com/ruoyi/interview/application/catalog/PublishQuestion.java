package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

@FunctionalInterface
public interface PublishQuestion {

    Result handle(Command command);

    record Command(
            ResourceId questionId,
            ResourceId questionVersionId,
            ResourceId rubricVersionId,
            AggregateVersion expectedVersion,
            String reviewReasonCode,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
            DomainPreconditions.requireNonNull(rubricVersionId, "rubricVersionId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            reviewReasonCode = DomainPreconditions.requireText(reviewReasonCode, "reviewReasonCode");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }

    record Result(
            ResourceId publicationId,
            PublishedQuestionSnapshot publishedQuestion,
            AggregateVersion version
    ) {
        public Result {
            DomainPreconditions.requireNonNull(publicationId, "publicationId");
            DomainPreconditions.requireNonNull(publishedQuestion, "publishedQuestion");
            DomainPreconditions.requireNonNull(version, "questionAggregateVersion");
        }
    }
}
