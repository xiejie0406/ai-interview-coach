package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** MVP 同步推进器：提交下一道已确认计划题，或在题目耗尽时完成会话。 */
@FunctionalInterface
public interface ProgressInterview {

    InterviewSessionSnapshot handle(Command command);

    record Command(ResourceId sessionId, AggregateVersion expectedVersion, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }
}
