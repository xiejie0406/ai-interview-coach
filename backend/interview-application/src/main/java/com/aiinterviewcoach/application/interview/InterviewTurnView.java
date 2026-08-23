package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.domain.interview.TurnKind;
import com.aiinterviewcoach.domain.interview.TurnState;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.Optional;

/** 恢复快照中的 Turn；questionText 只有 owner 查询且页面需要时才返回。 */
public record InterviewTurnView(
        ResourceId turnId,
        int sequence,
        TurnKind kind,
        TurnState state,
        Optional<String> questionText,
        Optional<ResourceId> answerVersionId
) {
    public InterviewTurnView {
        DomainPreconditions.requireNonNull(turnId, "turnId");
        DomainPreconditions.require(sequence > 0,
                com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "turn sequence must be positive");
        DomainPreconditions.requireNonNull(kind, "turnKind");
        DomainPreconditions.requireNonNull(state, "turnState");
        questionText = questionText == null ? Optional.empty() : questionText;
        answerVersionId = answerVersionId == null ? Optional.empty() : answerVersionId;
        questionText.ifPresent(text -> DomainPreconditions.require(text.length() <= 30_000,
                com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "question text exceeds maximum length"));
        switch (state) {
            case PLANNED -> DomainPreconditions.require(questionText.isEmpty() && answerVersionId.isEmpty(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_STATE,
                    "planned turn cannot expose committed facts");
            case QUESTION_COMMITTED -> DomainPreconditions.require(questionText.isPresent()
                            && answerVersionId.isEmpty(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_STATE,
                    "committed question view is inconsistent");
            case ANSWER_CONFIRMED, CLOSED -> DomainPreconditions.require(questionText.isPresent()
                            && answerVersionId.isPresent(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_STATE,
                    "answered turn view is missing immutable facts");
            case SKIPPED, CANCELLED, FAILED -> DomainPreconditions.require(answerVersionId.isEmpty(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_STATE,
                    "unanswered terminal turn cannot expose an answer");
        }
    }

    @Override
    public String toString() {
        return "InterviewTurnView[turnId=" + turnId + ", sequence=" + sequence + ", kind=" + kind
                + ", state=" + state + ", questionText=<redacted>, answerVersionId=" + answerVersionId + "]";
    }
}
