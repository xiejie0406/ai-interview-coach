package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.util.Objects;

public record AdenRunnerSessionPrincipal(
        AdenWorkspaceId workspaceId,
        AdenRunnerId runnerId,
        AdenSessionId sessionId,
        long sessionEpoch) {
    public AdenRunnerSessionPrincipal {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(runnerId, "runnerId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionEpoch < 1) throw new IllegalArgumentException("sessionEpoch 必须为正数");
    }
}
