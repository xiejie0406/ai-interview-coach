package com.ruoyi.aps.domain.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionRunStateMachineTest
{
    @Test
    void followsApprovedHappyPaths()
    {
        var empty = ExecutionTransitionContext.empty();
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.READY, ExecutionAction.START, empty))
                .isEqualTo(ExecutionRunStatus.RUNNING);
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.RUNNING, ExecutionAction.PAUSE, empty))
                .isEqualTo(ExecutionRunStatus.PAUSED);
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.PAUSED, ExecutionAction.RESUME, empty))
                .isEqualTo(ExecutionRunStatus.RUNNING);
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.RUNNING,
                ExecutionAction.FINISH_PROCESSING, empty)).isEqualTo(ExecutionRunStatus.COMPLETED);
    }

    @Test
    void waitsForQualityAndOnlyCompletesAfterEveryDispositionIsResolved()
    {
        var gate = new ExecutionTransitionContext(true, true, true, false);
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.RUNNING,
                ExecutionAction.FINISH_PROCESSING, gate)).isEqualTo(ExecutionRunStatus.WAIT_QUALITY);
        assertThatThrownBy(() -> ExecutionRunStateMachine.transition(ExecutionRunStatus.WAIT_QUALITY,
                ExecutionAction.COMPLETE_QUALITY, gate)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("待检");

        var resolved = new ExecutionTransitionContext(true, true, true, true);
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.WAIT_QUALITY,
                ExecutionAction.COMPLETE_QUALITY, resolved)).isEqualTo(ExecutionRunStatus.COMPLETED);
    }

    @Test
    void permitsOnlyControlledCancellation()
    {
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.READY, ExecutionAction.CANCEL,
                ExecutionTransitionContext.empty())).isEqualTo(ExecutionRunStatus.CANCELLED);
        assertThatThrownBy(() -> ExecutionRunStateMachine.transition(ExecutionRunStatus.RUNNING,
                ExecutionAction.CANCEL, ExecutionTransitionContext.empty()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("直接取消");
        assertThatThrownBy(() -> ExecutionRunStateMachine.transition(ExecutionRunStatus.PAUSED,
                ExecutionAction.CANCEL, new ExecutionTransitionContext(false, true, false, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("剩余数量处置");
        assertThat(ExecutionRunStateMachine.transition(ExecutionRunStatus.PAUSED, ExecutionAction.CANCEL,
                new ExecutionTransitionContext(false, true, true, true)))
                .isEqualTo(ExecutionRunStatus.CANCELLED);
    }

    @Test
    void neverReopensTerminalRun()
    {
        for (ExecutionRunStatus status : new ExecutionRunStatus[] {
                ExecutionRunStatus.COMPLETED, ExecutionRunStatus.CANCELLED })
        {
            assertThat(status.isTerminal()).isTrue();
            for (ExecutionAction action : ExecutionAction.values())
            {
                assertThatThrownBy(() -> ExecutionRunStateMachine.transition(status, action,
                        ExecutionTransitionContext.empty())).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("终态");
            }
        }
    }
}
