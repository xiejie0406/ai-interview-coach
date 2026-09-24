package com.ruoyi.aden.api.operator;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;

import java.time.Instant;

public record WorkspaceSnapshot(
        String workspaceId,
        String displayName,
        String role,
        String status,
        String version,
        Instant createdAt) {

    public static WorkspaceSnapshot from(AdenWorkspaceMembership membership) {
        return new WorkspaceSnapshot(
                membership.workspace().id().value(),
                membership.workspace().displayName(),
                membership.role().name(),
                membership.workspace().status().name(),
                Long.toString(membership.workspace().version()),
                membership.workspace().createdAt());
    }
}
