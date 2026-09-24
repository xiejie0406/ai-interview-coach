package com.ruoyi.aden.application.event;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.List;

/** Outbox 可靠投递的事务端口；实际 SSE fan-out 在后续阶段接入。 */
public interface AdenOutboxRecoveryPort {
    List<AdenOutboxMessage> claimReady(String claimToken, Instant now, Instant claimedUntil, int limit);

    void markPublished(AdenWorkspaceId workspaceId, String outboxId, String claimToken, Instant publishedAt);

    void markRetry(AdenWorkspaceId workspaceId, String outboxId, String claimToken,
                   String error, Instant nextAvailableAt, Instant updatedAt);

    void markDead(AdenWorkspaceId workspaceId, String outboxId, String claimToken,
                  String error, Instant updatedAt);
}
