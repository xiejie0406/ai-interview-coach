package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.domain.interview.InterviewMode;
import com.ruoyi.interview.domain.interview.InterviewPlanState;
import com.ruoyi.interview.domain.interview.UsageEstimate;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Optional;

/** 与 OpenAPI InterviewPlanView 对齐，不提前暴露计划中的问题内容。 */
public record InterviewPlanView(
        ResourceId id,
        int planVersionNo,
        InterviewPlanState state,
        InterviewMode mode,
        int questionCount,
        int followUpBudget,
        UsageEstimate estimatedUsage,
        Optional<ResourceId> reservationId,
        Instant expiresAt,
        AggregateVersion version
) {
    public InterviewPlanView {
        DomainPreconditions.requireNonNull(id, "planId");
        DomainPreconditions.require(planVersionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "planVersionNo must be positive");
        DomainPreconditions.requireNonNull(state, "planState");
        DomainPreconditions.requireNonNull(mode, "mode");
        DomainPreconditions.require(questionCount > 0, DomainErrorCode.INVALID_ARGUMENT,
                "questionCount must be positive");
        DomainPreconditions.require(followUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "followUpBudget must not be negative");
        DomainPreconditions.requireNonNull(estimatedUsage, "estimatedUsage");
        reservationId = reservationId == null ? Optional.empty() : reservationId;
        DomainPreconditions.requireNonNull(expiresAt, "expiresAt");
        DomainPreconditions.requireNonNull(version, "planAggregateVersion");
    }
}
