package com.ruoyi.aden.domain.workspace;

import java.time.Instant;
import java.util.Objects;

/** 跨 Workspace 或非成员访问的内部最小审计，不保存目标资源内容。 */
public record AdenWorkspaceForbiddenAudit(
        String auditEventId,
        AdenWorkspaceId requestedWorkspaceId,
        long actorUserId,
        String correlationId,
        Instant occurredAt) {

    public AdenWorkspaceForbiddenAudit {
        Objects.requireNonNull(auditEventId, "auditEventId");
        Objects.requireNonNull(requestedWorkspaceId, "requestedWorkspaceId");
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId 必须为正数");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
