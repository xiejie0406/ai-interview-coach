package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.domain.learning.LearningPlan;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.List;
import java.util.Optional;

/** Learning owner persistence port；所有查询显式 tenant + owner。 */
public interface LearningPlanRepository {

    Optional<LearningPlan> find(TenantId tenantId, ResourceId learningPlanId);

    Optional<LearningPlan> findByItem(TenantId tenantId, ResourceId learningItemId);

    Page findByOwner(TenantId tenantId, UserId userId, Optional<String> cursor, int limit);

    void save(LearningPlan learningPlan);

    record Page(List<LearningPlan> items, Optional<String> nextCursor) {
        public Page {
            items = List.copyOf(items == null ? List.of() : items);
            nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
        }
    }
}
