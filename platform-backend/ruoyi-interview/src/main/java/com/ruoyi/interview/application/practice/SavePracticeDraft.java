package com.ruoyi.interview.application.practice;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

@FunctionalInterface
public interface SavePracticeDraft {

    PracticeAttemptView handle(Command command);

    record Command(
            ResourceId attemptId,
            String answerText,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(attemptId, "attemptId");
            answerText = DomainPreconditions.requireNonNull(answerText, "answerText");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[attemptId=" + attemptId
                    + ", answerText=<redacted>, expectedVersion="
                    + expectedVersion + ", context=" + context + "]";
        }
    }
}
