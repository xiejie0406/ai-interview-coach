package com.ruoyi.interview.application.practice.port;

import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.domain.practice.PracticeAttempt;

import java.util.List;
import java.util.Optional;

public interface PracticeRepository {

    Optional<PracticeAttempt> find(TenantId tenantId, ResourceId attemptId);

    Page findHistory(TenantId tenantId, UserId userId, Optional<String> cursor, int limit);

    void save(PracticeAttempt attempt);

    record Page(List<PracticeAttempt> items, Optional<String> nextCursor) {
        public Page {
            items = List.copyOf(items == null ? List.of() : items);
            nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
        }
    }
}
