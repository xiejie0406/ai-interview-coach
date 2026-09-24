package com.ruoyi.aden.domain.workspace;

import java.time.Instant;
import java.util.Objects;

public record AdenWorkspaceMembership(
        AdenWorkspace workspace,
        long ruoYiUserId,
        AdenWorkspaceRole role,
        AdenWorkspaceMemberStatus memberStatus,
        Instant membershipCreatedAt) {

    public AdenWorkspaceMembership {
        Objects.requireNonNull(workspace, "workspace");
        if (ruoYiUserId <= 0) throw new IllegalArgumentException("ruoYiUserId 必须为正数");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(memberStatus, "memberStatus");
        Objects.requireNonNull(membershipCreatedAt, "membershipCreatedAt");
    }
}
