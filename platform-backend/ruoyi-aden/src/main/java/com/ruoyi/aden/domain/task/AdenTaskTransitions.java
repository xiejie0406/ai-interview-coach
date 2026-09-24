package com.ruoyi.aden.domain.task;

import java.util.HashMap;
import java.util.Map;

/**
 * Aden 任务的确定性状态迁移表。
 *
 * <p>取消先进入 {@link AdenTaskState#CANCEL_REQUESTED}，只有 Runner 或协调器在安全点确认后
 * 才能进入 {@link AdenTaskState#CANCELED}。这避免把可能仍在途的外部动作伪装成已取消。</p>
 */
public final class AdenTaskTransitions {
    private static final Map<Key, AdenTaskState> TRANSITIONS = transitions();

    private AdenTaskTransitions() {
    }

    public static AdenTaskState apply(AdenTaskState current, AdenTaskActor actor, AdenTaskCommand command) {
        if (current == null || actor == null || command == null) {
            throw new IllegalArgumentException("当前状态、actor 和命令不能为空");
        }
        AdenTaskState next = TRANSITIONS.get(new Key(current, actor, command));
        if (next == null) {
            throw new AdenTaskTransitionException(current, actor, command);
        }
        return next;
    }

    public static boolean allows(AdenTaskState current, AdenTaskActor actor, AdenTaskCommand command) {
        return current != null && actor != null && command != null
                && TRANSITIONS.containsKey(new Key(current, actor, command));
    }

    private static Map<Key, AdenTaskState> transitions() {
        Map<Key, AdenTaskState> result = new HashMap<>();
        rule(result, AdenTaskState.DRAFT, AdenTaskActor.OPERATOR,
                AdenTaskCommand.SUBMIT_FOR_VALIDATION, AdenTaskState.VALIDATING);
        rule(result, AdenTaskState.VALIDATING, AdenTaskActor.VALIDATOR,
                AdenTaskCommand.VALIDATION_PASSED, AdenTaskState.QUEUED);
        rule(result, AdenTaskState.VALIDATING, AdenTaskActor.VALIDATOR,
                AdenTaskCommand.VALIDATION_FAILED, AdenTaskState.FAILED);
        for (AdenTaskState state : new AdenTaskState[]{AdenTaskState.VALIDATING, AdenTaskState.QUEUED,
                AdenTaskState.RUNNING, AdenTaskState.WAITING_USER, AdenTaskState.WAITING_EXTERNAL}) {
            rule(result, state, AdenTaskActor.OPERATOR,
                    AdenTaskCommand.REQUEST_CANCEL, AdenTaskState.CANCEL_REQUESTED);
        }
        rule(result, AdenTaskState.QUEUED, AdenTaskActor.RUNNER,
                AdenTaskCommand.START, AdenTaskState.RUNNING);
        rule(result, AdenTaskState.RUNNING, AdenTaskActor.RUNNER,
                AdenTaskCommand.WAIT_FOR_USER, AdenTaskState.WAITING_USER);
        rule(result, AdenTaskState.RUNNING, AdenTaskActor.RUNNER,
                AdenTaskCommand.WAIT_FOR_EXTERNAL, AdenTaskState.WAITING_EXTERNAL);
        rule(result, AdenTaskState.RUNNING, AdenTaskActor.RUNNER,
                AdenTaskCommand.COMPLETE, AdenTaskState.SUCCEEDED);
        rule(result, AdenTaskState.RUNNING, AdenTaskActor.RUNNER,
                AdenTaskCommand.FAIL, AdenTaskState.FAILED);
        rule(result, AdenTaskState.WAITING_USER, AdenTaskActor.COORDINATOR,
                AdenTaskCommand.RESUME, AdenTaskState.RUNNING);
        rule(result, AdenTaskState.WAITING_EXTERNAL, AdenTaskActor.COORDINATOR,
                AdenTaskCommand.RESUME, AdenTaskState.RUNNING);
        rule(result, AdenTaskState.WAITING_USER, AdenTaskActor.COORDINATOR,
                AdenTaskCommand.FAIL, AdenTaskState.FAILED);
        rule(result, AdenTaskState.WAITING_EXTERNAL, AdenTaskActor.COORDINATOR,
                AdenTaskCommand.FAIL, AdenTaskState.FAILED);
        rule(result, AdenTaskState.CANCEL_REQUESTED, AdenTaskActor.RUNNER,
                AdenTaskCommand.CONFIRM_CANCELED, AdenTaskState.CANCELED);
        rule(result, AdenTaskState.CANCEL_REQUESTED, AdenTaskActor.COORDINATOR,
                AdenTaskCommand.CONFIRM_CANCELED, AdenTaskState.CANCELED);
        return Map.copyOf(result);
    }

    private static void rule(Map<Key, AdenTaskState> result, AdenTaskState state,
                             AdenTaskActor actor, AdenTaskCommand command, AdenTaskState target) {
        if (result.put(new Key(state, actor, command), target) != null) {
            throw new IllegalStateException("重复的 Task 状态迁移规则");
        }
    }

    private record Key(AdenTaskState state, AdenTaskActor actor, AdenTaskCommand command) { }
}
