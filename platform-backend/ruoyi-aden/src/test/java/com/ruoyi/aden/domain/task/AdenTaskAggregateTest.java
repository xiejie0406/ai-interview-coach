package com.ruoyi.aden.domain.task;

import com.ruoyi.aden.application.task.AdenOperatorTaskCommandAdapter;
import com.ruoyi.aden.contract.OperatorTaskCommand;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenTaskAggregateTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-13T02:10:00Z");

    @Test
    void transitionReturnsANewAggregateAndVersionedEvent() {
        AdenTask draft = task(AdenTaskState.DRAFT, 0);

        AdenTaskTransition transition = draft.transition(
                AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION, CREATED_AT.plusSeconds(1));

        assertEquals(AdenTaskState.DRAFT, draft.state());
        assertEquals(AdenTaskState.VALIDATING, transition.task().state());
        assertEquals(1, transition.task().version().value());
        assertEquals(1, transition.event().aggregateVersion().value());
        assertEquals(AdenTaskState.DRAFT, transition.event().from());
        assertEquals(AdenTaskState.VALIDATING, transition.event().to());
    }

    @Test
    void operatorCannotForgeInternalCommands() {
        AdenTask validating = task(AdenTaskState.VALIDATING, 1);

        assertThrows(AdenTaskTransitionException.class, () -> validating.transition(
                AdenTaskActor.OPERATOR, AdenTaskCommand.VALIDATION_PASSED, CREATED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> OperatorTaskCommand.parseWireValue("VALIDATION_PASSED"));
    }

    @Test
    void publicCommandsMapToDistinctPermissions() {
        var submit = AdenOperatorTaskCommandAdapter.map(OperatorTaskCommand.SUBMIT_FOR_VALIDATION);
        var cancel = AdenOperatorTaskCommandAdapter.map(OperatorTaskCommand.REQUEST_CANCEL);

        assertEquals(AdenTaskCommand.SUBMIT_FOR_VALIDATION, submit.command());
        assertEquals("aden:task:command", submit.permission());
        assertEquals(AdenTaskCommand.REQUEST_CANCEL, cancel.command());
        assertEquals("aden:task:cancel", cancel.permission());
    }

    @Test
    void aggregateRejectsVersionOverflowAndTimeRegression() {
        assertThrows(IllegalStateException.class, () -> task(AdenTaskState.DRAFT, Long.MAX_VALUE)
                .transition(AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION,
                        CREATED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> task(AdenTaskState.DRAFT, 0)
                .transition(AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION,
                        CREATED_AT.minusSeconds(1)));
    }

    private static AdenTask task(AdenTaskState state, long version) {
        return new AdenTask(
                new AdenWorkspaceId("11111111-1111-4111-8111-111111111111"),
                new AdenTaskId("22222222-2222-4222-8222-222222222222"),
                AdenTaskType.SYNTHETIC_CORE,
                AdenCapabilityCode.CORE,
                "合成任务",
                state,
                new AdenTaskVersion(version),
                new AdenCorrelationId("33333333-3333-4333-8333-333333333333"),
                42,
                CREATED_AT,
                CREATED_AT);
    }
}
