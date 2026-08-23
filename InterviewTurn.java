package com.aiinterviewcoach.domain.interview;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Optional;

/**
 * Turn 只能由同包的 InterviewSession 推进；Agent/adapter 得到的是候选或只读视图，不能直接赋状态。
 */
public final class InterviewTurn {

    private final ResourceId id;
    private final int sequence;
    private final PlannedQuestion plannedQuestion;
    private final ResourceId parentTurnId;
    private final TurnKind kind;
    private TurnState state;
    private QuestionPrompt questionPrompt;
    private InterviewAnswerVersion answerVersion;
    private Instant committedAt;

    InterviewTurn(
            ResourceId id,
            int sequence,
            PlannedQuestion plannedQuestion,
            ResourceId parentTurnId,
            TurnKind kind
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "turnId");
        DomainPreconditions.require(sequence > 0, DomainErrorCode.INVALID_ARGUMENT,
                "turn sequence must be positive");
        this.sequence = sequence;
        this.plannedQuestion = DomainPreconditions.requireNonNull(plannedQuestion, "plannedQuestion");
        this.parentTurnId = parentTurnId;
        this.kind = DomainPreconditions.requireNonNull(kind, "turnKind");
        if (kind == TurnKind.PRIMARY) {
            DomainPreconditions.require(parentTurnId == null, DomainErrorCode.INVALID_ARGUMENT,
                    "primary turn cannot have a parent");
        } else {
            DomainPreconditions.requireNonNull(parentTurnId, "non-primary turn parentTurnId");
        }
        this.state = TurnState.PLANNED;
    }

    /** 从 Session owner persistence 重建；外部 adapter 仍不能调用状态迁移方法。 */
    public static InterviewTurn rehydrate(
            ResourceId id,
            int sequence,
            PlannedQuestion plannedQuestion,
            ResourceId parentTurnId,
            TurnKind kind,
            TurnState state,
            QuestionPrompt questionPrompt,
            InterviewAnswerVersion answerVersion,
            Instant committedAt
    ) {
        InterviewTurn turn = new InterviewTurn(id, sequence, plannedQuestion, parentTurnId, kind);
        turn.state = DomainPreconditions.requireNonNull(state, "turnState");
        turn.questionPrompt = questionPrompt;
        turn.answerVersion = answerVersion;
        turn.committedAt = committedAt;
        turn.assertConsistentState();
        return turn;
    }

    public ResourceId id() {
        return id;
    }

    public int sequence() {
        return sequence;
    }

    public PlannedQuestion plannedQuestion() {
        return plannedQuestion;
    }

    public Optional<ResourceId> parentTurnId() {
        return Optional.ofNullable(parentTurnId);
    }

    public TurnKind kind() {
        return kind;
    }

    public TurnState state() {
        return state;
    }

    public Optional<QuestionPrompt> questionPrompt() {
        return Optional.ofNullable(questionPrompt);
    }

    public Optional<InterviewAnswerVersion> answerVersion() {
        return Optional.ofNullable(answerVersion);
    }

    public Optional<Instant> committedAt() {
        return Optional.ofNullable(committedAt);
    }

    void commitQuestion(QuestionPrompt prompt, Instant committedAt) {
        DomainPreconditions.require(state == TurnState.PLANNED, DomainErrorCode.INVALID_STATE,
                "question can only be committed to a planned turn");
        this.questionPrompt = DomainPreconditions.requireNonNull(prompt, "questionPrompt");
        this.committedAt = DomainPreconditions.requireNonNull(committedAt, "questionCommittedAt");
        state = TurnState.QUESTION_COMMITTED;
    }

    void confirmAnswer(InterviewAnswerVersion answerVersion) {
        DomainPreconditions.require(state == TurnState.QUESTION_COMMITTED, DomainErrorCode.INVALID_STATE,
                "answer can only be confirmed for a committed question");
        DomainPreconditions.require(answerVersion.turnId().equals(id), DomainErrorCode.STALE_TURN,
                "answer belongs to another turn");
        this.answerVersion = answerVersion;
        state = TurnState.ANSWER_CONFIRMED;
    }

    void close() {
        DomainPreconditions.require(state == TurnState.ANSWER_CONFIRMED, DomainErrorCode.INVALID_STATE,
                "only answered turn can be closed");
        state = TurnState.CLOSED;
    }

    void skip() {
        DomainPreconditions.require(state == TurnState.PLANNED || state == TurnState.QUESTION_COMMITTED,
                DomainErrorCode.INVALID_STATE, "turn cannot be skipped from current state");
        state = TurnState.SKIPPED;
    }

    void cancel() {
        DomainPreconditions.require(state == TurnState.PLANNED || state == TurnState.QUESTION_COMMITTED,
                DomainErrorCode.INVALID_STATE, "turn cannot be cancelled from current state");
        state = TurnState.CANCELLED;
    }

    void fail() {
        DomainPreconditions.require(state == TurnState.PLANNED || state == TurnState.QUESTION_COMMITTED,
                DomainErrorCode.INVALID_STATE, "turn cannot fail from current state");
        state = TurnState.FAILED;
    }

    private void assertConsistentState() {
        if (state == TurnState.PLANNED) {
            DomainPreconditions.require(questionPrompt == null && answerVersion == null && committedAt == null,
                    DomainErrorCode.INVALID_STATE, "planned turn cannot contain committed facts");
            return;
        }
        if (state == TurnState.QUESTION_COMMITTED) {
            DomainPreconditions.require(questionPrompt != null && committedAt != null && answerVersion == null,
                    DomainErrorCode.INVALID_STATE, "committed question turn is inconsistent");
            return;
        }
        if (state == TurnState.ANSWER_CONFIRMED || state == TurnState.CLOSED) {
            DomainPreconditions.require(questionPrompt != null && committedAt != null && answerVersion != null,
                    DomainErrorCode.INVALID_STATE, "answered turn is missing immutable facts");
            DomainPreconditions.require(answerVersion.turnId().equals(id), DomainErrorCode.STALE_TURN,
                    "answer version belongs to another turn");
            return;
        }
        DomainPreconditions.require(answerVersion == null,
                DomainErrorCode.INVALID_STATE, "terminal unanswered turn cannot contain an answer");
        DomainPreconditions.require((questionPrompt == null) == (committedAt == null),
                DomainErrorCode.INVALID_STATE, "terminal turn question facts are inconsistent");
    }
}
