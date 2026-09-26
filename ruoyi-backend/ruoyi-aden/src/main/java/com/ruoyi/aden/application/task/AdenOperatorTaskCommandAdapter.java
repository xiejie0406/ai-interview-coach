package com.ruoyi.aden.application.task;

import com.ruoyi.aden.contract.OperatorTaskCommand;
import com.ruoyi.aden.domain.task.AdenTaskActor;
import com.ruoyi.aden.domain.task.AdenTaskCommand;

import java.util.Objects;

/** 公开 Operator DTO 到内部命令的唯一显式映射。 */
public final class AdenOperatorTaskCommandAdapter {
    private AdenOperatorTaskCommandAdapter() { }

    public static MappedCommand map(OperatorTaskCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case SUBMIT_FOR_VALIDATION -> new MappedCommand(
                    AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION, "aden:task:command");
            case REQUEST_CANCEL -> new MappedCommand(
                    AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL, "aden:task:cancel");
        };
    }

    public record MappedCommand(AdenTaskActor actor, AdenTaskCommand command, String permission) {
        public MappedCommand {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(permission, "permission");
        }
    }
}
