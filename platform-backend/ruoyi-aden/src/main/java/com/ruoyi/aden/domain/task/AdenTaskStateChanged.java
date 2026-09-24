package com.ruoyi.aden.domain.task;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

public record AdenTaskStateChanged(
        AdenWorkspaceId workspaceId,
        AdenTaskId taskId,
        AdenTaskState from,
        AdenTaskState to,
        AdenTaskCommand command,
        AdenTaskActor actor,
        AdenTaskVersion aggregateVersion,
        AdenCorrelationId correlationId,
        Instant occurredAt) {

    public AdenTaskStateChanged {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(aggregateVersion, "aggregateVersion");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
