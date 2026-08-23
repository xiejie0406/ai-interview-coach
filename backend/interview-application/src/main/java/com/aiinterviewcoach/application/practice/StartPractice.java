package com.aiinterviewcoach.application.practice;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;

@FunctionalInterface
public interface StartPractice {

    PracticeAttemptView handle(Command command);

    record Command(
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
            DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }
}
