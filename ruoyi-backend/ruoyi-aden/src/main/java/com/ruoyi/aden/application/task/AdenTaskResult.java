package com.ruoyi.aden.application.task;

import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskVersion;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.util.Objects;

public record AdenTaskResult(
        AdenWorkspaceId workspaceId,
        AdenTaskId taskId,
        AdenTaskState state,
        AdenTaskVersion version,
        String reasonCode,
        boolean replayed) {
    public AdenTaskResult {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(version, "version");
    }
}
