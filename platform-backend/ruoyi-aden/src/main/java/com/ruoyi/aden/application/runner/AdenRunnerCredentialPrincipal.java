package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.util.Objects;

public record AdenRunnerCredentialPrincipal(
        AdenWorkspaceId workspaceId,
        AdenRunnerId runnerId,
        AdenCredentialId credentialId,
        long credentialEpoch) {
    public AdenRunnerCredentialPrincipal {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(runnerId, "runnerId");
        Objects.requireNonNull(credentialId, "credentialId");
        if (credentialEpoch < 1) throw new IllegalArgumentException("credentialEpoch 必须为正数");
    }
}
