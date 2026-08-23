package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

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
