package com.aiinterviewcoach.domain.interview;

import com.aiinterviewcoach.domain.platform.AggregateRoot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 面试业务状态的唯一聚合根。流式 delta、ASR partial、TTS 播放和 Agent 候选均不能直接推进它。
 */
public final class InterviewSession extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final InterviewPlanReference planReference;
    private final InterviewMode mode;
    private final List<InterviewTurn> turns;
    private SessionState state;
    private int lastStableSequence;
    private Instant startedAt;
    private Instant pausedAt;
    private Instant completingAt;
    private Instant completedAt;
    private Instant recoveryExpiresAt;
    private String failureCode;
    private AggregateVersion version;

    private InterviewSession(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            InterviewPlanReference planReference,
            InterviewMode mode,
            SessionState state,
            List<InterviewTurn> turns,
            int lastStableSequence,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "interviewSessionId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.planReference = DomainPreconditions.requireNonNull(planReference, "planReference");
        this.mode = DomainPreconditions.requireNonNull(mode, "interviewMode");
        this.state = DomainPreconditions.requireNonNull(state, "sessionState");
        this.turns = new ArrayList<>(turns == null ? List.of() : turns);
        DomainPreconditions.require(lastStableSequence >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "last stable sequence must not be negative");
        DomainPreconditions.require(lastStableSequence <= this.turns.size(), DomainErrorCode.INVALID_STATE,
                "last stable sequence exceeds turn count");
        for (int index = 0; index < this.turns.size(); index++) {
            DomainPreconditions.require(this.turns.get(index).sequence() == index + 1,
                    DomainErrorCode.INVALID_STATE, "turn sequences must be contiguous from one");
        }
        DomainPreconditions.require(new HashSet<>(this.turns.stream().map(InterviewTurn::id).toList()).size()
                        == this.turns.size(),
                DomainErrorCode.INVALID_STATE, "session contains duplicate turn ids");
        this.lastStableSequence = lastStableSequence;
        this.version = DomainPreconditions.requireNonNull(version, "sessionVersion");
        assertConsistentTurns();
    }

    public static InterviewSession ready(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            InterviewPlanReference planReference,
            InterviewMode mode,
            EventContext context
    ) {
        InterviewSession session = new InterviewSession(id, tenantId, userId, planReference, mode,
                SessionState.READY, List.of(), 0, AggregateVersion.initial());
        session.recordEvent("interview.session.ready", tenantId, id, session.version, context,
                Map.of("planId", planReference.planId().value().toString(), "mode", mode.name()));
        return session;
    }

    /** 从 tenant/resource-owner scoped persistence 重建；不产生事件。 */
    public static InterviewSession rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            InterviewPlanReference planReference,
            InterviewMode mode,
            SessionState state,
            List<InterviewTurn> turns,
            int lastStableSequence,
            Instant startedAt,
            Instant pausedAt,
            Instant completingAt,
            Instant completedAt,
            Instant recoveryExpiresAt,
            String failureCode,
            AggregateVersion version
    ) {
        InterviewSession session = new InterviewSession(id, tenantId, userId, planReference, mode,
                state, turns, lastStableSequence, version);
        session.startedAt = startedAt;
        session.pausedAt = pausedAt;
        session.completingAt = completingAt;
        session.completedAt = completedAt;
        session.recoveryExpiresAt = recoveryExpiresAt;
        session.failureCode = failureCode;
        session.assertConsistentState();
        return session;
    }

    public ResourceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public InterviewPlanReference planReference() {
        return planReference;
    }

    public InterviewMode mode() {
        return mode;
    }

    public SessionState state() {
        return state;
    }

    public List<InterviewTurn> turns() {
        return List.copyOf(turns);
    }

    public int lastStableSequence() {
        return lastStableSequence;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> pausedAt() {
        return Optional.ofNullable(pausedAt);
    }

    public Optional<Instant> completingAt() {
        return Optional.ofNullable(completingAt);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<Instant> recoveryExpiresAt() {
        return Optional.ofNullable(recoveryExpiresAt);
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public AggregateVersion version() {
        return version;
    }

    public Set<SessionCommandType> allowedCommands() {
        return SessionPolicy.allowedCommands(state);
    }

    public void start(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.START, expectedVersion);
        state = SessionState.IN_PROGRESS;
        startedAt = context.occurredAt();
        bump("interview.session.started", context, Map.of());
    }

    public void openTurn(
            ResourceId turnId,
            PlannedQuestion plannedQuestion,
            TurnKind kind,
            ResourceId parentTurnId,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.requireNonNull(turnId, "turnId");
        DomainPreconditions.requireNonNull(plannedQuestion, "plannedQuestion");
        DomainPreconditions.requireNonNull(kind, "turnKind");
        DomainPreconditions.requireNonNull(context, "eventContext");
        if (kind == TurnKind.PRIMARY) {
            DomainPreconditions.require(parentTurnId == null, DomainErrorCode.INVALID_ARGUMENT,
                    "primary turn cannot have a parent");
        } else {
            DomainPreconditions.requireNonNull(parentTurnId, "non-primary turn parentTurnId");
        }
        DomainPreconditions.require(state == SessionState.IN_PROGRESS, DomainErrorCode.INVALID_STATE,
                "turn can only be opened while interview is in progress");
        Optional<InterviewTurn> currentTurn = currentOpenTurn();
        DomainPreconditions.require(currentTurn.isEmpty()
                        || currentTurn.orElseThrow().state() == TurnState.ANSWER_CONFIRMED,
                DomainErrorCode.SESSION_ALREADY_ACTIVE,
                "session already has an open turn");
        DomainPreconditions.require(turns.stream().noneMatch(existing -> existing.id().equals(turnId)),
                DomainErrorCode.VERSION_CONFLICT, "turn id has already been used in this session");
        long primaryTurns = turns.stream().filter(turn -> turn.kind() == TurnKind.PRIMARY).count();
        long nonPrimaryTurns = turns.size() - primaryTurns;
        if (kind == TurnKind.PRIMARY) {
            DomainPreconditions.require(primaryTurns < planReference.questionCount(),
                    DomainErrorCode.POLICY_DENIED, "plan question budget is exhausted");
            DomainPreconditions.require(plannedQuestion.position() == primaryTurns + 1,
                    DomainErrorCode.POLICY_DENIED, "primary questions must follow confirmed plan order");
            DomainPreconditions.require(planReference.questionAtPosition((int) primaryTurns + 1)
                            .equals(plannedQuestion),
                    DomainErrorCode.POLICY_DENIED, "primary question is not part of the confirmed plan");
        } else {
            DomainPreconditions.require(nonPrimaryTurns < planReference.followUpBudget(),
                    DomainErrorCode.POLICY_DENIED, "plan follow-up budget is exhausted");
            InterviewTurn parent = turns.stream()
                    .filter(turn -> turn.id().equals(parentTurnId))
                    .findFirst()
                    .orElseThrow(() -> new com.aiinterviewcoach.domain.platform.DomainException(
                            DomainErrorCode.STALE_TURN, "follow-up parent turn is not in this session"));
            DomainPreconditions.require(parent.state() == TurnState.ANSWER_CONFIRMED
                            || parent.state() == TurnState.CLOSED,
                    DomainErrorCode.INVALID_STATE, "follow-up parent turn must have a confirmed answer");
            DomainPreconditions.require(parent.plannedQuestion().equals(plannedQuestion),
                    DomainErrorCode.POLICY_DENIED, "follow-up must remain within the parent question scope");
            long questionFollowUps = turns.stream()
                    .filter(turn -> turn.kind() != TurnKind.PRIMARY)
                    .filter(turn -> turn.plannedQuestion().equals(plannedQuestion))
                    .count();
            DomainPreconditions.require(questionFollowUps < plannedQuestion.followUpBudget(),
                    DomainErrorCode.POLICY_DENIED, "question follow-up budget is exhausted");
        }
        closeAnsweredTurnIfPresent();
        int nextSequence = turns.size() + 1;
        turns.add(new InterviewTurn(turnId, nextSequence, plannedQuestion, parentTurnId, kind));
        bump("interview.turn.planned", context,
                Map.of("turnId", turnId.value().toString(), "sequence", Integer.toString(nextSequence)));
    }

    public void commitQuestion(
            ResourceId turnId,
            QuestionPrompt prompt,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == SessionState.IN_PROGRESS, DomainErrorCode.INVALID_STATE,
                "question can only be committed while interview is in progress");
        InterviewTurn turn = requireCurrentTurn(turnId);
        turn.commitQuestion(prompt, context.occurredAt());
        lastStableSequence = turn.sequence();
        bump("interview.question.committed", context,
                Map.of("turnId", turn.id().value().toString(),
                        "sequence", Integer.toString(turn.sequence())));
    }

    public void confirmAnswer(
            ResourceId turnId,
            InterviewAnswerVersion answerVersion,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        commandAllowed(SessionCommandType.SUBMIT_ANSWER, expectedVersion);
        DomainPreconditions.require(answerVersion.tenantId().equals(tenantId)
                        && answerVersion.sessionId().equals(id)
                        && answerVersion.confirmedBy().equals(userId),
                DomainErrorCode.OWNERSHIP_DENIED, "answer belongs to another session or owner");
        InterviewTurn turn = requireCurrentTurn(turnId);
        turn.confirmAnswer(answerVersion);
        lastStableSequence = turn.sequence();
        bump("interview.turn.answer_confirmed", context,
                Map.of("turnId", turn.id().value().toString(),
                        "answerVersionId", answerVersion.id().value().toString(),
                        "sequence", Integer.toString(turn.sequence())));
    }

    public void skipCurrentTurn(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.SKIP, expectedVersion);
        InterviewTurn turn = currentOpenTurn().orElseThrow(() ->
                new com.aiinterviewcoach.domain.platform.DomainException(
                        DomainErrorCode.STALE_TURN, "session has no active turn to skip"));
        turn.skip();
        lastStableSequence = turn.sequence();
        bump("interview.turn.skipped", context,
                Map.of("turnId", turn.id().value().toString(),
                        "sequence", Integer.toString(turn.sequence())));
    }

    public void pause(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.PAUSE, expectedVersion);
        state = SessionState.PAUSED;
        pausedAt = context.occurredAt();
        bump("interview.session.paused", context, Map.of());
    }

    public void resume(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.RESUME, expectedVersion);
        state = SessionState.IN_PROGRESS;
        pausedAt = null;
        bump("interview.session.resumed", context, Map.of());
    }

    public void markRecoverableFailure(
            String failureCode,
            Instant recoveryExpiresAt,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == SessionState.IN_PROGRESS || state == SessionState.PAUSED,
                DomainErrorCode.INVALID_STATE, "session cannot enter recoverable failure from current state");
        DomainPreconditions.requireNonNull(context, "eventContext");
        String checkedFailureCode = DomainPreconditions.requireText(failureCode, "failureCode");
        Instant checkedRecoveryExpiresAt = DomainPreconditions.requireNonNull(
                recoveryExpiresAt, "recoveryExpiresAt");
        DomainPreconditions.require(checkedRecoveryExpiresAt.isAfter(context.occurredAt()),
                DomainErrorCode.INVALID_ARGUMENT,
                "recovery deadline must be in the future");
        this.failureCode = checkedFailureCode;
        this.recoveryExpiresAt = checkedRecoveryExpiresAt;
        pausedAt = null;
        state = SessionState.FAILED_RECOVERABLE;
        bump("interview.session.recoverable_failure", context, Map.of("failureCode", this.failureCode));
    }

    public void recover(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.RECOVER, expectedVersion);
        DomainPreconditions.require(context.occurredAt().isBefore(recoveryExpiresAt), DomainErrorCode.INVALID_STATE,
                "session recovery window has expired");
        state = SessionState.IN_PROGRESS;
        pausedAt = null;
        failureCode = null;
        recoveryExpiresAt = null;
        bump("interview.session.recovered", context, Map.of());
    }

    public void failFinal(String failureCode, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == SessionState.FAILED_RECOVERABLE,
                DomainErrorCode.INVALID_STATE, "only a recoverable failure can become final failure");
        this.failureCode = DomainPreconditions.requireText(failureCode, "failureCode");
        terminateCurrentTurnForFailure();
        state = SessionState.FAILED_FINAL;
        bump("interview.session.failed_final", context, Map.of("failureCode", this.failureCode));
    }

    public void beginCompletion(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.COMPLETE, expectedVersion);
        cancelUnansweredCurrentTurn();
        closeAnsweredTurnIfPresent();
        clearTransientState();
        state = SessionState.COMPLETING;
        completingAt = context.occurredAt();
        bump("interview.session.completing", context, Map.of());
    }

    public void complete(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == SessionState.COMPLETING, DomainErrorCode.INVALID_STATE,
                "session must be completing before it can complete");
        state = SessionState.COMPLETED;
        completedAt = context.occurredAt();
        bump("interview.session.completed", context, Map.of());
    }

    public void cancel(AggregateVersion expectedVersion, EventContext context) {
        commandAllowed(SessionCommandType.CANCEL, expectedVersion);
        cancelUnansweredCurrentTurn();
        closeAnsweredTurnIfPresent();
        clearTransientState();
        state = SessionState.CANCELLED;
        bump("interview.session.cancelled", context, Map.of());
    }

    private Optional<InterviewTurn> currentOpenTurn() {
        if (turns.isEmpty()) {
            return Optional.empty();
        }
        InterviewTurn last = turns.get(turns.size() - 1);
        return switch (last.state()) {
            case PLANNED, QUESTION_COMMITTED, ANSWER_CONFIRMED -> Optional.of(last);
            case CLOSED, SKIPPED, CANCELLED, FAILED -> Optional.empty();
        };
    }

    private InterviewTurn requireCurrentTurn(ResourceId turnId) {
        InterviewTurn turn = currentOpenTurn().orElseThrow(() ->
                new com.aiinterviewcoach.domain.platform.DomainException(
                        DomainErrorCode.STALE_TURN, "session has no active turn"));
        DomainPreconditions.require(turn.id().equals(turnId), DomainErrorCode.STALE_TURN,
                "turn is not the current active turn");
        return turn;
    }

    private void closeAnsweredTurnIfPresent() {
        currentOpenTurn().filter(turn -> turn.state() == TurnState.ANSWER_CONFIRMED)
                .ifPresent(InterviewTurn::close);
    }

    private void cancelUnansweredCurrentTurn() {
        currentOpenTurn().filter(turn -> turn.state() == TurnState.PLANNED
                        || turn.state() == TurnState.QUESTION_COMMITTED)
                .ifPresent(InterviewTurn::cancel);
    }

    private void terminateCurrentTurnForFailure() {
        currentOpenTurn().ifPresent(turn -> {
            if (turn.state() == TurnState.ANSWER_CONFIRMED) {
                turn.close();
            } else {
                turn.fail();
            }
        });
    }

    private void clearTransientState() {
        pausedAt = null;
        recoveryExpiresAt = null;
        failureCode = null;
    }

    private void commandAllowed(SessionCommandType command, AggregateVersion expectedVersion) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(SessionPolicy.permits(state, command), DomainErrorCode.INVALID_STATE,
                "session command is not allowed in current state");
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private void assertConsistentState() {
        boolean cancelledBeforeStart = state == SessionState.CANCELLED && startedAt == null;
        if (state == SessionState.READY) {
            DomainPreconditions.require(startedAt == null && turns.isEmpty(), DomainErrorCode.INVALID_STATE,
                    "ready session cannot contain started facts");
        } else if (!cancelledBeforeStart) {
            DomainPreconditions.requireNonNull(startedAt, "startedAt");
        }
        if (cancelledBeforeStart) {
            DomainPreconditions.require(turns.isEmpty(), DomainErrorCode.INVALID_STATE,
                    "session cancelled before start cannot contain turns");
        }
        DomainPreconditions.require((state == SessionState.PAUSED) == (pausedAt != null),
                DomainErrorCode.INVALID_STATE, "pausedAt is inconsistent with session state");
        boolean completingOrCompleted = state == SessionState.COMPLETING || state == SessionState.COMPLETED;
        DomainPreconditions.require(completingOrCompleted == (completingAt != null),
                DomainErrorCode.INVALID_STATE, "completingAt is inconsistent with session state");
        DomainPreconditions.require((state == SessionState.COMPLETED) == (completedAt != null),
                DomainErrorCode.INVALID_STATE, "completedAt is inconsistent with session state");
        boolean failed = state == SessionState.FAILED_RECOVERABLE || state == SessionState.FAILED_FINAL;
        DomainPreconditions.require(failed == (failureCode != null)
                        && failed == (recoveryExpiresAt != null),
                DomainErrorCode.INVALID_STATE, "failure recovery facts are inconsistent with session state");
        boolean terminalWithoutOpenTurn = state == SessionState.COMPLETING
                || state == SessionState.COMPLETED
                || state == SessionState.CANCELLED
                || state == SessionState.FAILED_FINAL;
        if (terminalWithoutOpenTurn) {
            DomainPreconditions.require(currentOpenTurn().isEmpty(), DomainErrorCode.INVALID_STATE,
                    "terminal session cannot retain an open turn");
        }
        if (startedAt != null && pausedAt != null) {
            DomainPreconditions.require(!pausedAt.isBefore(startedAt), DomainErrorCode.INVALID_STATE,
                    "pausedAt cannot precede startedAt");
        }
        if (startedAt != null && completingAt != null) {
            DomainPreconditions.require(!completingAt.isBefore(startedAt), DomainErrorCode.INVALID_STATE,
                    "completingAt cannot precede startedAt");
        }
        if (completingAt != null && completedAt != null) {
            DomainPreconditions.require(!completedAt.isBefore(completingAt), DomainErrorCode.INVALID_STATE,
                    "completedAt cannot precede completingAt");
        }
    }

    private void assertConsistentTurns() {
        int primaryCount = 0;
        int followUpCount = 0;
        int derivedLastStableSequence = 0;
        Map<ResourceId, InterviewTurn> earlierTurns = new HashMap<>();
        Map<PlannedQuestion, Integer> followUpsByQuestion = new HashMap<>();
        for (int index = 0; index < turns.size(); index++) {
            InterviewTurn turn = turns.get(index);
            boolean open = turn.state() == TurnState.PLANNED
                    || turn.state() == TurnState.QUESTION_COMMITTED
                    || turn.state() == TurnState.ANSWER_CONFIRMED;
            DomainPreconditions.require(!open || index == turns.size() - 1,
                    DomainErrorCode.INVALID_STATE, "only the latest turn may remain open");
            if (turn.kind() == TurnKind.PRIMARY) {
                primaryCount++;
                DomainPreconditions.require(turn.plannedQuestion().position() == primaryCount,
                        DomainErrorCode.INVALID_STATE, "primary turns must follow confirmed plan order");
                DomainPreconditions.require(planReference.questionAtPosition(primaryCount)
                                .equals(turn.plannedQuestion()),
                        DomainErrorCode.INVALID_STATE, "rehydrated primary turn is outside confirmed plan");
            } else {
                followUpCount++;
                ResourceId parentId = turn.parentTurnId().orElseThrow();
                InterviewTurn parent = earlierTurns.get(parentId);
                DomainPreconditions.require(parent != null && parent.state() == TurnState.CLOSED,
                        DomainErrorCode.INVALID_STATE, "follow-up parent must be an earlier closed turn");
                DomainPreconditions.require(parent.plannedQuestion().equals(turn.plannedQuestion()),
                        DomainErrorCode.INVALID_STATE, "follow-up changed its parent question scope");
                int used = followUpsByQuestion.merge(turn.plannedQuestion(), 1, Integer::sum);
                DomainPreconditions.require(used <= turn.plannedQuestion().followUpBudget(),
                        DomainErrorCode.INVALID_STATE, "question follow-up budget is exceeded");
            }
            turn.answerVersion().ifPresent(answer -> DomainPreconditions.require(
                    answer.tenantId().equals(tenantId)
                            && answer.sessionId().equals(id)
                            && answer.turnId().equals(turn.id())
                            && answer.confirmedBy().equals(userId),
                    DomainErrorCode.OWNERSHIP_DENIED,
                    "rehydrated answer belongs to another session or owner"));
            if (turn.committedAt().isPresent() || turn.state() == TurnState.SKIPPED) {
                derivedLastStableSequence = turn.sequence();
            }
            earlierTurns.put(turn.id(), turn);
        }
        DomainPreconditions.require(primaryCount <= planReference.questionCount(),
                DomainErrorCode.INVALID_STATE, "session exceeds confirmed plan question budget");
        DomainPreconditions.require(followUpCount <= planReference.followUpBudget(),
                DomainErrorCode.INVALID_STATE, "session exceeds confirmed plan follow-up budget");
        DomainPreconditions.require(lastStableSequence == derivedLastStableSequence,
                DomainErrorCode.INVALID_STATE, "last stable sequence does not match turn facts");
    }
}
