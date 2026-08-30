package com.ruoyi.interview.application.interview.port;

import com.ruoyi.interview.domain.interview.PlannedQuestion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.TimeBudget;

import java.util.List;
import java.util.Set;

/**
 * Catalog 查询之上的确定性选择端口。实现不得调用 LLM 决定发布状态、预算、权益或隐藏范围。
 */
public interface PlanQuestionSelectionPort {

    List<PlannedQuestion> select(SelectionRequest request);

    record SelectionRequest(
            TenantId tenantId,
            Set<String> topicCodes,
            Set<String> targetRoles,
            Set<String> difficulties,
            TimeBudget totalTimeBudget,
            int desiredQuestionCount,
            int totalFollowUpBudget
    ) {
        public SelectionRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            topicCodes = Set.copyOf(DomainPreconditions.requireNonEmpty(topicCodes, "topicCodes"));
            targetRoles = Set.copyOf(DomainPreconditions.requireNonEmpty(targetRoles, "targetRoles"));
            difficulties = Set.copyOf(DomainPreconditions.requireNonEmpty(difficulties, "difficulties"));
            DomainPreconditions.requireNonNull(totalTimeBudget, "totalTimeBudget");
            DomainPreconditions.require(desiredQuestionCount > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "desired question count must be positive");
            DomainPreconditions.require(totalFollowUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                    "total follow-up budget must not be negative");
        }
    }
}
