package com.ruoyi.interview.application.practice;

import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.util.Optional;

@FunctionalInterface
public interface ListPracticeHistory {

    CursorPage<PracticeAttemptView> handle(Query query);

    record Query(Optional<String> cursor, int limit, QueryContext context) {
        public Query {
            cursor = cursor == null ? Optional.empty() : cursor;
            DomainPreconditions.require(limit > 0 && limit <= 100, DomainErrorCode.INVALID_ARGUMENT,
                    "history limit must be between 1 and 100");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }
}
