package com.ruoyi.aden.domain.runner;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

public record AdenRunner(
        AdenWorkspaceId workspaceId,
        AdenRunnerId id,
        String name,
        AdenRunnerPresence presence,
        AdenCapabilitySnapshot capabilitySnapshot,
        long currentSessionEpoch,
        long currentCredentialEpoch,
        long version,
        Instant lastSeenAt) {
    public AdenRunner {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        name = name.trim();
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("runner name 必须为 1..80 字符");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(capabilitySnapshot, "capabilitySnapshot");
        if (currentSessionEpoch < 0 || currentCredentialEpoch < 0 || version < 0) {
            throw new IllegalArgumentException("Runner epoch/version 不得为负数");
        }
    }

    public AdenRunner nextSession(Instant now) {
        if (currentSessionEpoch == Long.MAX_VALUE) throw new IllegalStateException("session epoch 已耗尽");
        return new AdenRunner(workspaceId, id, name, AdenRunnerPresence.ONLINE, capabilitySnapshot,
                currentSessionEpoch + 1, currentCredentialEpoch, version + 1, Objects.requireNonNull(now));
    }
}
