package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** REST 权威快照；恢复时先调用它，再重连 SSE/WS。 */
@FunctionalInterface
public interface RecoverInterview {

    InterviewSessionSnapshot handle(Query query);

    record Query(ResourceId sessionId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }
}
