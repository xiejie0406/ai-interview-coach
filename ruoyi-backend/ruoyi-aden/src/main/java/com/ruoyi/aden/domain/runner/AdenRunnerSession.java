package com.ruoyi.aden.domain.runner;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

public record AdenRunnerSession(
        AdenWorkspaceId workspaceId,
        AdenSessionId id,
        AdenRunnerId runnerId,
        AdenCredentialId credentialId,
        AdenSessionStatus status,
        long epoch,
        int capacity,
        int inFlight,
        long heartbeatSequence,
        Instant heartbeatAt,
        Instant expiresAt,
        long version) {
    public AdenRunnerSession {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(runnerId, "runnerId");
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(status, "status");
        if (epoch < 1 || capacity < 1 || inFlight < 0 || inFlight > capacity
                || heartbeatSequence < 0 || version < 0) throw new IllegalArgumentException("Session 计数非法");
        Objects.requireNonNull(heartbeatAt, "heartbeatAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(heartbeatAt)) throw new IllegalArgumentException("Session expiry 必须晚于 heartbeat");
    }

    public boolean activeAt(Instant now, long runnerEpoch) {
        return status == AdenSessionStatus.ACTIVE && epoch == runnerEpoch && now.isBefore(expiresAt);
    }

    public int remainingCapacity() { return capacity - inFlight; }
}
