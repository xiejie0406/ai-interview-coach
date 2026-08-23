package com.ruoyi.interview.application.practice;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;

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
