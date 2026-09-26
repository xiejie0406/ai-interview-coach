package com.ruoyi.aden.domain.workspace;

import java.util.Objects;
import java.util.UUID;

/** 跨边界使用规范小写 UUID 文本的 Workspace 标识。 */
public record AdenWorkspaceId(String value) {
    public AdenWorkspaceId {
        Objects.requireNonNull(value, "workspaceId");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("workspaceId 必须是规范小写 UUID");
        }
    }
}
