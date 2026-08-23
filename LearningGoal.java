package com.aiinterviewcoach.domain.learning;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** 学习目标；descriptionRef 指向正文或模板，不在日志输出正文。 */
@Deprecated(forRemoval = false)
public record LearningGoal(
        ResourceId goalId,
        String skillKey,
        String descriptionRef,
        int priority
) {

    public LearningGoal {
        DomainPreconditions.requireNonNull(goalId, "goalId");
        skillKey = DomainPreconditions.requireText(skillKey, "skillKey");
        descriptionRef = DomainPreconditions.requireText(descriptionRef, "descriptionRef");
        DomainPreconditions.require(priority > 0,
                com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "priority must be positive");
    }

    @Override
    public String toString() {
        return "LearningGoal[goalId=" + goalId + ", skillKey=" + skillKey
                + ", descriptionRef=<redacted>, priority=" + priority + "]";
    }
}
