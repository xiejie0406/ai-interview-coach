package com.ruoyi.aden.domain.workspace;

import java.time.Instant;
import java.util.Objects;

public record AdenWorkspace(
        AdenWorkspaceId id,
        String displayName,
        AdenWorkspaceStatus status,
        long version,
        long createdByRuoYiUserId,
        Instant createdAt,
        Instant updatedAt) {

    public AdenWorkspace {
        Objects.requireNonNull(id, "id");
        displayName = requireDisplayName(displayName);
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version 不得为负数");
        if (createdByRuoYiUserId <= 0) throw new IllegalArgumentException("createdByRuoYiUserId 必须为正数");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static String requireDisplayName(String value) {
        if (value == null) throw new IllegalArgumentException("displayName 不能为空");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 80 || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("displayName 必须为 1..80 个非换行字符");
        }
        return normalized;
    }
}
