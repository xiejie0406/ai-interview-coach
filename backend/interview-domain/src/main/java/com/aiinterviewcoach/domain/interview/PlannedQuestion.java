package com.aiinterviewcoach.domain.interview;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.TimeBudget;

/** 确认计划中的题目和预算快照。 */
public record PlannedQuestion(
        int position,
        ImmutableVersionRef questionVersion,
        ImmutableVersionRef rubricVersion,
        String topicCode,
        TimeBudget timeBudget,
        int followUpBudget
) {

    public PlannedQuestion {
        DomainPreconditions.require(position > 0, DomainErrorCode.INVALID_ARGUMENT,
                "planned question position must be positive");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        topicCode = DomainPreconditions.requireText(topicCode, "topicCode");
        DomainPreconditions.requireNonNull(timeBudget, "timeBudget");
        DomainPreconditions.require(followUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "follow-up budget must not be negative");
    }
}
