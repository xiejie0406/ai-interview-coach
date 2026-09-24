package com.ruoyi.aden.domain.task;

/** 非法或不安全的任务状态迁移。 */
public final class AdenTaskTransitionException extends IllegalStateException {
    public AdenTaskTransitionException(AdenTaskState state, AdenTaskActor actor, AdenTaskCommand command) {
        super("任务状态 " + state + " 不允许 actor " + actor + " 执行命令 " + command);
    }
}
