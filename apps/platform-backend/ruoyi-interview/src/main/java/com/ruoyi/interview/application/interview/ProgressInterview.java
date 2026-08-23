package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

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
