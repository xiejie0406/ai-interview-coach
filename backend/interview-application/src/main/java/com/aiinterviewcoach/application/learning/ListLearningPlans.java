package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.learning.LearningPlan;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface ListLearningPlans {
    Result handle(Query query);

    record Query(Optional<String> cursor, int limit, QueryContext context) {
        public Query {
            cursor = cursor == null ? Optional.empty() : cursor;
            cursor.ifPresent(value -> DomainPreconditions.require(value.length() <= 512,
                    DomainErrorCode.INVALID_ARGUMENT, "learning plan cursor is too long"));
            DomainPreconditions.require(limit >= 1 && limit <= 100, DomainErrorCode.INVALID_ARGUMENT,
                    "learning plan page limit is invalid");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record Result(List<LearningPlan> items, Optional<String> nextCursor) {
        public Result {
            items = List.copyOf(items == null ? List.of() : items);
            nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
        }
    }
}
