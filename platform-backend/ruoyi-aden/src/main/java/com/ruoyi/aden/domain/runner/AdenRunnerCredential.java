package com.ruoyi.aden.domain.runner;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

public record AdenRunnerCredential(
        AdenWorkspaceId workspaceId,
        AdenCredentialId id,
        AdenRunnerId runnerId,
        long epoch,
        String keyedDigest,
        String pepperKeyId,
        AdenCredentialStatus status,
        Instant issuedAt,
        Instant expiresAt) {
    public AdenRunnerCredential {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(runnerId, "runnerId");
        if (epoch < 1) throw new IllegalArgumentException("credential epoch 必须为正数");
        if (keyedDigest == null || !keyedDigest.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("credential digest 必须为 64 位小写十六进制");
        }
        if (pepperKeyId == null || pepperKeyId.isBlank() || pepperKeyId.length() > 64) {
            throw new IllegalArgumentException("pepperKeyId 不合法");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("凭据到期时间必须晚于签发时间");
    }

    public boolean activeAt(Instant now) { return status == AdenCredentialStatus.ACTIVE && now.isBefore(expiresAt); }
}
