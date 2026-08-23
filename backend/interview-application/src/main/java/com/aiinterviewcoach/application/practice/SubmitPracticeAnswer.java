package com.aiinterviewcoach.application.practice;

import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.practice.AnswerSource;

import java.util.Optional;

/** 回答事实与 Evaluation Job/Outbox 必须在同一本地事务受理。 */
@FunctionalInterface
public interface SubmitPracticeAnswer {

    Result handle(Command command);

    record Command(
            ResourceId attemptId,
            AnswerSource source,
            String answerText,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(attemptId, "attemptId");
            DomainPreconditions.requireNonNull(source, "answerSource");
            answerText = DomainPreconditions.requireText(answerText, "answerText");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[attemptId=" + attemptId + ", source=" + source
                    + ", answerText=<redacted>, expectedVersion=" + expectedVersion
                    + ", context=" + context + "]";
        }
    }

    record Result(
            PracticeAttemptView attempt,
            ResourceId answerVersionId,
            Optional<OperationAccepted> evaluationOperation
    ) {
        public Result {
            DomainPreconditions.requireNonNull(attempt, "attempt");
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            evaluationOperation = evaluationOperation == null ? Optional.empty() : evaluationOperation;
        }
    }
}
