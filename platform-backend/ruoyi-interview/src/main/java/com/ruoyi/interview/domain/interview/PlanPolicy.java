package com.ruoyi.interview.domain.interview;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.TimeBudget;

import java.time.Duration;
import java.util.List;

/** 计划题目时长与追问预算的确定性门；LLM 不能修改这些上限。 */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    public static void validate(
            List<PlannedQuestion> questions,
            TimeBudget totalTimeBudget,
            int totalFollowUpBudget
    ) {
        DomainPreconditions.requireNonEmpty(questions, "plannedQuestions");
        DomainPreconditions.requireNonNull(totalTimeBudget, "totalTimeBudget");
        DomainPreconditions.require(totalFollowUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "total follow-up budget must not be negative");
        Duration allocated = questions.stream()
                .map(question -> question.timeBudget().duration())
                .reduce(Duration.ZERO, Duration::plus);
        DomainPreconditions.require(allocated.compareTo(totalTimeBudget.duration()) <= 0,
                DomainErrorCode.POLICY_DENIED, "question time allocation exceeds plan time budget");
        int allocatedFollowUps = questions.stream().mapToInt(PlannedQuestion::followUpBudget).sum();
        DomainPreconditions.require(allocatedFollowUps <= totalFollowUpBudget,
                DomainErrorCode.POLICY_DENIED, "question follow-up allocation exceeds plan budget");
    }
}
