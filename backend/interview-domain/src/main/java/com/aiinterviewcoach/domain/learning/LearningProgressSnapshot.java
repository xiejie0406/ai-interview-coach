package com.aiinterviewcoach.domain.learning;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** 不含敏感正文的学习进度快照。 */
public record LearningProgressSnapshot(
        int totalTasks,
        int completedTasks,
        int totalEstimatedMinutes
) {

    public LearningProgressSnapshot {
        DomainPreconditions.require(totalTasks >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "totalTasks must not be negative");
        DomainPreconditions.require(completedTasks >= 0 && completedTasks <= totalTasks,
                DomainErrorCode.INVALID_ARGUMENT, "completedTasks must be between zero and totalTasks");
        DomainPreconditions.require(totalEstimatedMinutes >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "totalEstimatedMinutes must not be negative");
    }
}

