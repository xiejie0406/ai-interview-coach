package com.ruoyi.interview.application.agent.interview;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.PromptRef;
import com.ruoyi.interview.domain.platform.SchemaRef;

import java.util.Optional;

/**
 * Agent 候选输出，不是 SessionCommand。application 必须校验状态、题目范围、单问、预算和安全后再提交领域命令。
 */
public record InterviewerCandidateAction(
        InterviewerActionType type,
        Optional<String> questionText,
        String reasonCode,
        PromptRef promptRef,
        SchemaRef schemaRef
) {
    public InterviewerCandidateAction {
        DomainPreconditions.requireNonNull(type, "interviewerActionType");
        questionText = questionText == null ? Optional.empty() : questionText;
        reasonCode = DomainPreconditions.requireText(reasonCode, "reasonCode");
        DomainPreconditions.require(reasonCode.matches("[A-Z][A-Z0-9_]{0,95}"),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "reasonCode is invalid");
        DomainPreconditions.requireNonNull(promptRef, "promptRef");
        DomainPreconditions.requireNonNull(schemaRef, "schemaRef");
        boolean asksQuestion = type == InterviewerActionType.ASK
                || type == InterviewerActionType.FOLLOW_UP
                || type == InterviewerActionType.CLARIFY;
        DomainPreconditions.require(asksQuestion == questionText.isPresent(),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "candidate action question fields are inconsistent");
    }

    @Override
    public String toString() {
        return "InterviewerCandidateAction[type=" + type
                + ", questionText=<redacted>, reasonCode=<redacted>"
                + ", promptRef=" + promptRef + ", schemaRef=" + schemaRef + "]";
    }
}
