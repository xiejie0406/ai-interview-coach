package com.ruoyi.aden.application.event;

import com.ruoyi.aden.domain.event.AdenOutboxState;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/** 已领取且可在事务外发布的不可变 Outbox 事实。 */
public record AdenOutboxMessage(
        AdenWorkspaceId workspaceId,
        String outboxId,
        String eventId,
        long eventSequence,
        String eventType,
        String payloadJson,
        String correlationId,
        String consumer,
        AdenOutboxState state,
        int attempts,
        String claimToken,
        Instant claimedUntil) {

    public AdenOutboxMessage {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(outboxId, "outboxId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(claimedUntil, "claimedUntil");
        if (state != AdenOutboxState.CLAIMED || attempts < 1 || eventSequence < 1) {
            throw new IllegalArgumentException("Outbox 领取事实不完整");
        }
    }
}
