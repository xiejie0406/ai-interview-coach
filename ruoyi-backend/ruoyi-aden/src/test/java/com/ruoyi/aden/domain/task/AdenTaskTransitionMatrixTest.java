package com.ruoyi.aden.domain.task;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenTaskTransitionMatrixTest {
    private static final Set<Rule> RULES = Set.of(
            r(AdenTaskState.DRAFT, AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION),
            r(AdenTaskState.VALIDATING, AdenTaskActor.VALIDATOR, AdenTaskCommand.VALIDATION_PASSED),
            r(AdenTaskState.VALIDATING, AdenTaskActor.VALIDATOR, AdenTaskCommand.VALIDATION_FAILED),
            r(AdenTaskState.VALIDATING, AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL),
            r(AdenTaskState.QUEUED, AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL),
            r(AdenTaskState.RUNNING, AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL),
            r(AdenTaskState.WAITING_USER, AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL),
            r(AdenTaskState.WAITING_EXTERNAL, AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL),
            r(AdenTaskState.QUEUED, AdenTaskActor.RUNNER, AdenTaskCommand.START),
            r(AdenTaskState.QUEUED, AdenTaskActor.COLLECTOR, AdenTaskCommand.START),
            r(AdenTaskState.RUNNING, AdenTaskActor.COLLECTOR, AdenTaskCommand.COMPLETE),
            r(AdenTaskState.RUNNING, AdenTaskActor.COLLECTOR, AdenTaskCommand.FAIL),
            r(AdenTaskState.RUNNING, AdenTaskActor.RUNNER, AdenTaskCommand.WAIT_FOR_USER),
            r(AdenTaskState.RUNNING, AdenTaskActor.RUNNER, AdenTaskCommand.WAIT_FOR_EXTERNAL),
            r(AdenTaskState.RUNNING, AdenTaskActor.RUNNER, AdenTaskCommand.COMPLETE),
            r(AdenTaskState.RUNNING, AdenTaskActor.RUNNER, AdenTaskCommand.FAIL),
            r(AdenTaskState.WAITING_USER, AdenTaskActor.COORDINATOR, AdenTaskCommand.RESUME),
            r(AdenTaskState.WAITING_EXTERNAL, AdenTaskActor.COORDINATOR, AdenTaskCommand.RESUME),
            r(AdenTaskState.WAITING_USER, AdenTaskActor.COORDINATOR, AdenTaskCommand.FAIL),
            r(AdenTaskState.WAITING_EXTERNAL, AdenTaskActor.COORDINATOR, AdenTaskCommand.FAIL),
            r(AdenTaskState.CANCEL_REQUESTED, AdenTaskActor.RUNNER, AdenTaskCommand.CONFIRM_CANCELED),
            r(AdenTaskState.CANCEL_REQUESTED, AdenTaskActor.COORDINATOR, AdenTaskCommand.CONFIRM_CANCELED));

    @ParameterizedTest
    @MethodSource("allCombinations")
    void everyStateActorCommandCombinationIsExplicit(AdenTaskState state,
                                                       AdenTaskActor actor,
                                                       AdenTaskCommand command) {
        Rule rule = r(state, actor, command);
        if (RULES.contains(rule)) {
            assertEquals(true, AdenTaskTransitions.allows(state, actor, command));
        } else {
            assertFalse(AdenTaskTransitions.allows(state, actor, command));
            assertThrows(AdenTaskTransitionException.class,
                    () -> AdenTaskTransitions.apply(state, actor, command));
        }
    }

    private static Stream<Arguments> allCombinations() {
        return Stream.of(AdenTaskState.values()).flatMap(state ->
                Stream.of(AdenTaskActor.values()).flatMap(actor ->
                        Stream.of(AdenTaskCommand.values()).map(command -> Arguments.of(state, actor, command))));
    }

    private static Rule r(AdenTaskState state, AdenTaskActor actor, AdenTaskCommand command) {
        return new Rule(state, actor, command);
    }

    private record Rule(AdenTaskState state, AdenTaskActor actor, AdenTaskCommand command) { }
}
