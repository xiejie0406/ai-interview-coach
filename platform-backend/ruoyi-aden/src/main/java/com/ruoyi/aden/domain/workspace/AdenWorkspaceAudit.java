package com.ruoyi.aden.domain.workspace;

import java.time.Instant;
import java.util.Objects;

public record AdenWorkspaceAudit(
        String auditEventId,
        AdenWorkspaceId workspaceId,
        String actionCode,
        String resourceId,
        long actorUserId,
        String correlationId,
        Instant occurredAt) {

    public AdenWorkspaceAudit {
        Objects.requireNonNull(auditEventId, "auditEventId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(actionCode, "actionCode");
        Objects.requireNonNull(resourceId, "resourceId");
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId 必须为正数");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
