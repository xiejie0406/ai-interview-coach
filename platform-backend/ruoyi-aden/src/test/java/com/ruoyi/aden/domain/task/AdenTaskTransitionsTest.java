package com.ruoyi.aden.domain.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdenTaskTransitionsTest {
    @Test
    void followsTheMinimumSuccessfulLifecycle() {
        AdenTaskState state = AdenTaskState.DRAFT;
        state = AdenTaskTransitions.apply(state, AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION);
        state = AdenTaskTransitions.apply(state, AdenTaskActor.VALIDATOR, AdenTaskCommand.VALIDATION_PASSED);
        state = AdenTaskTransitions.apply(state, AdenTaskActor.RUNNER, AdenTaskCommand.START);
        state = AdenTaskTransitions.apply(state, AdenTaskActor.RUNNER, AdenTaskCommand.WAIT_FOR_EXTERNAL);
        state = AdenTaskTransitions.apply(state, AdenTaskActor.COORDINATOR, AdenTaskCommand.RESUME);
        state = AdenTaskTransitions.apply(state, AdenTaskActor.RUNNER, AdenTaskCommand.COMPLETE);

        assertEquals(AdenTaskState.SUCCEEDED, state);
    }

    @Test
    void cancelMustBeConfirmedAtASafePoint() {
        AdenTaskState requested = AdenTaskTransitions.apply(
                AdenTaskState.RUNNING, AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL);

        assertEquals(AdenTaskState.CANCEL_REQUESTED, requested);
        assertEquals(AdenTaskState.CANCELED,
                AdenTaskTransitions.apply(requested, AdenTaskActor.RUNNER, AdenTaskCommand.CONFIRM_CANCELED));
    }

    @Test
    void terminalTasksCannotBeResumedOrCompletedAgain() {
        assertThrows(AdenTaskTransitionException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.SUCCEEDED,
                        AdenTaskActor.COORDINATOR, AdenTaskCommand.RESUME));
        assertThrows(AdenTaskTransitionException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.CANCELED,
                        AdenTaskActor.RUNNER, AdenTaskCommand.COMPLETE));
        assertThrows(AdenTaskTransitionException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.FAILED,
                        AdenTaskActor.RUNNER, AdenTaskCommand.START));
    }

    @Test
    void draftCannotSkipValidationAndStart() {
        assertThrows(AdenTaskTransitionException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.DRAFT,
                        AdenTaskActor.RUNNER, AdenTaskCommand.START));
    }

    @Test
    void waitingForUserMustResumeBeforeCompletion() {
        assertEquals(AdenTaskState.RUNNING,
                AdenTaskTransitions.apply(AdenTaskState.WAITING_USER,
                        AdenTaskActor.COORDINATOR, AdenTaskCommand.RESUME));
        assertThrows(AdenTaskTransitionException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.WAITING_USER,
                        AdenTaskActor.RUNNER, AdenTaskCommand.COMPLETE));
        assertThrows(AdenTaskTransitionException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.WAITING_EXTERNAL,
                        AdenTaskActor.RUNNER, AdenTaskCommand.COMPLETE));
    }

    @Test
    void onlyFinalOutcomesAreTerminal() {
        assertTrue(AdenTaskState.SUCCEEDED.isTerminal());
        assertTrue(AdenTaskState.FAILED.isTerminal());
        assertTrue(AdenTaskState.CANCELED.isTerminal());
        assertFalse(AdenTaskState.CANCEL_REQUESTED.isTerminal());
        assertFalse(AdenTaskState.RUNNING.isTerminal());
    }

    @Test
    void nullStateOrCommandIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AdenTaskTransitions.apply(null, AdenTaskActor.RUNNER, AdenTaskCommand.START));
        assertThrows(IllegalArgumentException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.DRAFT, null, AdenTaskCommand.START));
        assertThrows(IllegalArgumentException.class,
                () -> AdenTaskTransitions.apply(AdenTaskState.DRAFT, AdenTaskActor.OPERATOR, null));
    }
}
