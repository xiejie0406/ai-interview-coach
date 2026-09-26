package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.event.AdenOutboxMessage;
import com.ruoyi.aden.application.event.AdenOutboxRecoveryPort;
import com.ruoyi.aden.domain.event.AdenOutboxState;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenOutboxMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenOutboxRow;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MyBatisAdenOutboxRecoveryRepository implements AdenOutboxRecoveryPort {
    private final AdenOutboxMapper mapper;

    public MyBatisAdenOutboxRecoveryRepository(AdenOutboxMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<AdenOutboxMessage> claimReady(String claimToken, Instant now,
                                              Instant claimedUntil, int limit) {
        List<AdenOutboxMessage> claimed = new ArrayList<>();
        for (AdenOutboxRow candidate : mapper.selectReadyForUpdate(
                AdenUtcDateTimeCodec.toDatabase(now), limit)) {
            requireOne(mapper.claim(candidate.getWorkspaceId(), candidate.getOutboxId(), claimToken,
                    AdenUtcDateTimeCodec.toDatabase(now),
                    AdenUtcDateTimeCodec.toDatabase(claimedUntil)), "claim");
            AdenOutboxRow row = mapper.selectClaimed(
                    candidate.getWorkspaceId(), candidate.getOutboxId(), claimToken);
            if (row == null) throw new IllegalStateException("已领取 Outbox 无法回读");
            claimed.add(toMessage(row));
        }
        return claimed;
    }

    @Override
    public void markPublished(AdenWorkspaceId workspaceId, String outboxId,
                              String claimToken, Instant publishedAt) {
        requireOne(mapper.markPublished(workspaceId.value(), outboxId, claimToken,
                AdenUtcDateTimeCodec.toDatabase(publishedAt)), "publish");
    }

    @Override
    public void markRetry(AdenWorkspaceId workspaceId, String outboxId, String claimToken,
                          String error, Instant nextAvailableAt, Instant updatedAt) {
        requireOne(mapper.markRetry(workspaceId.value(), outboxId, claimToken, error,
                AdenUtcDateTimeCodec.toDatabase(nextAvailableAt),
                AdenUtcDateTimeCodec.toDatabase(updatedAt)), "retry");
    }

    @Override
    public void markDead(AdenWorkspaceId workspaceId, String outboxId, String claimToken,
                         String error, Instant updatedAt) {
        requireOne(mapper.markDead(workspaceId.value(), outboxId, claimToken, error,
                AdenUtcDateTimeCodec.toDatabase(updatedAt)), "dead");
    }

    private static AdenOutboxMessage toMessage(AdenOutboxRow row) {
        return new AdenOutboxMessage(new AdenWorkspaceId(row.getWorkspaceId()), row.getOutboxId(),
                row.getEventId(), row.getEventSequence(), row.getEventType(), row.getPayloadJson(),
                row.getCorrelationId(), row.getConsumer(), AdenOutboxState.valueOf(row.getOutboxState()),
                row.getAttempts(), row.getClaimToken(),
                AdenUtcDateTimeCodec.fromDatabase(row.getClaimedUntil()));
    }

    private static void requireOne(int affected, String operation) {
        if (affected != 1) throw new IllegalStateException("Outbox " + operation + " 竞争失败");
    }
}
