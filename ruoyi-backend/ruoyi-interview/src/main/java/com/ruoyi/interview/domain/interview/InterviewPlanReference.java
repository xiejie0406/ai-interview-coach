package com.ruoyi.interview.domain.interview;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.List;

/** 确认后的不可变计划输入；Session 不读取可变计划草稿。 */
public record InterviewPlanReference(
        ResourceId planId,
        int planVersionNo,
        String contentHash,
        ResourceId usageReservationId,
        List<PlannedQuestion> questions,
        int followUpBudget
) {

    public InterviewPlanReference {
        DomainPreconditions.requireNonNull(planId, "planId");
        DomainPreconditions.require(planVersionNo > 0,
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "plan version number must be positive");
        contentHash = DomainPreconditions.requireText(contentHash, "planContentHash");
        DomainPreconditions.requireNonNull(usageReservationId, "usageReservationId");
        DomainPreconditions.requireNonEmpty(questions, "plannedQuestions");
        questions.forEach(question -> DomainPreconditions.requireNonNull(question, "plannedQuestion"));
        questions = List.copyOf(questions);
        for (int index = 0; index < questions.size(); index++) {
            DomainPreconditions.require(questions.get(index).position() == index + 1,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "planned question positions must be contiguous from one");
        }
        DomainPreconditions.require(new HashSet<>(questions.stream()
                        .map(question -> question.questionVersion().resourceId()).toList()).size() == questions.size(),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "confirmed plan contains duplicate question versions");
        DomainPreconditions.require(followUpBudget >= 0,
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "follow-up budget must not be negative");
        DomainPreconditions.require(questions.stream().mapToInt(PlannedQuestion::followUpBudget).sum()
                        <= followUpBudget,
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "question follow-up allocation exceeds confirmed plan budget");
    }

    public int questionCount() {
        return questions.size();
    }

    public PlannedQuestion questionAtPosition(int position) {
        DomainPreconditions.require(position > 0 && position <= questions.size(),
                com.ruoyi.interview.domain.platform.DomainErrorCode.POLICY_DENIED,
                "question position is outside confirmed plan");
        return questions.get(position - 1);
    }
}
