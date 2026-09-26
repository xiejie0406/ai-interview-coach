package com.ruoyi.interview.application.agent.interview;

import com.ruoyi.interview.domain.interview.InterviewMode;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TimeBudget;

import java.util.List;
import java.util.Optional;

/** Agent 只读取不可变引用、最小上下文和剩余预算。 */
public record InterviewAgentInput(
        ResourceId sessionId,
        int nextTurnSequence,
        InterviewMode mode,
        ImmutableVersionRef agentConfigVersion,
        ImmutableVersionRef questionVersion,
        ImmutableVersionRef rubricVersion,
        Optional<ConfirmedAnswerInput> previousConfirmedAnswer,
        List<String> allowedFollowUpTemplates,
        TimeBudget remainingTime,
        int remainingFollowUpBudget
) {
    public InterviewAgentInput {
        DomainPreconditions.requireNonNull(sessionId, "sessionId");
        DomainPreconditions.require(nextTurnSequence > 0, DomainErrorCode.INVALID_ARGUMENT,
                "next turn sequence must be positive");
        DomainPreconditions.requireNonNull(mode, "interviewMode");
        DomainPreconditions.requireNonNull(agentConfigVersion, "agentConfigVersion");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        previousConfirmedAnswer = previousConfirmedAnswer == null
                ? Optional.empty() : previousConfirmedAnswer;
        allowedFollowUpTemplates = List.copyOf(DomainPreconditions.requireNonNull(
                allowedFollowUpTemplates, "allowedFollowUpTemplates"));
        DomainPreconditions.requireNonNull(remainingTime, "remainingTime");
        DomainPreconditions.require(remainingFollowUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "remaining follow-up budget must not be negative");
    }

    @Override
    public String toString() {
        return "InterviewAgentInput[sessionId=" + sessionId + ", nextTurnSequence=" + nextTurnSequence
                + ", mode=" + mode + ", agentConfigVersion=" + agentConfigVersion
                + ", questionVersion=" + questionVersion + ", rubricVersion=" + rubricVersion
                + ", previousConfirmedAnswer=" + previousConfirmedAnswer
                + ", allowedFollowUpTemplates=<redacted:" + allowedFollowUpTemplates.size()
                + ">, remainingTime=" + remainingTime + ", remainingFollowUpBudget="
                + remainingFollowUpBudget + "]";
    }
}
