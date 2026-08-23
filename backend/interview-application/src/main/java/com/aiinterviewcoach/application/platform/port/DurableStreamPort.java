package com.aiinterviewcoach.application.platform.port;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.DurableStreamEvent;
import com.aiinterviewcoach.domain.platform.DurableStreamType;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL durable SSE 事实端口。调用方必须先完成 tenant + owner 授权；本端口仍会把
 * malformed、跨 tenant 和跨 stream cursor 合并为 UNKNOWN_OR_FOREIGN，避免泄漏存在性。
 */
public interface DurableStreamPort {

    DurableStreamEvent append(AppendCommand command);

    Optional<String> currentCursor(TenantId tenantId, DurableStreamType streamType, ResourceId streamId);

    ReplayResult replayAfter(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId,
            String cursor,
            int limit,
            Instant observedAt
    );

    /** 仅供受租约保护的 retention Job 调用；删除只允许推进 retention floor。 */
    int purgeExpiredPrefixes(Instant observedAt, int limit);

    record AppendCommand(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId,
            ResourceId eventId,
            ResourceId aggregateId,
            AggregateVersion aggregateVersion,
            String type,
            Instant occurredAt,
            int schemaVersion,
            CorrelationId correlationId,
            Map<String, String> data,
            Instant expiresAt
    ) {
        public AppendCommand {
            DomainPreconditions.requireNonNull(tenantId, "streamTenantId");
            DomainPreconditions.requireNonNull(streamType, "streamType");
            DomainPreconditions.requireNonNull(streamId, "streamId");
            DomainPreconditions.requireNonNull(eventId, "streamEventId");
            DomainPreconditions.requireNonNull(aggregateId, "streamAggregateId");
            DomainPreconditions.requireNonNull(aggregateVersion, "streamAggregateVersion");
            type = DomainPreconditions.requireText(type, "streamEventType");
            DomainPreconditions.requireNonNull(occurredAt, "streamOccurredAt");
            DomainPreconditions.require(schemaVersion > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "stream schema version must be positive");
            DomainPreconditions.requireNonNull(correlationId, "streamCorrelationId");
            data = Map.copyOf(new LinkedHashMap<>(data == null ? Map.of() : data));
            data.forEach((key, value) -> {
                DomainPreconditions.requireText(key, "stream data key");
                DomainPreconditions.requireText(value, "stream data value");
            });
            DomainPreconditions.requireNonNull(expiresAt, "streamExpiresAt");
            DomainPreconditions.require(expiresAt.isAfter(occurredAt), DomainErrorCode.INVALID_ARGUMENT,
                    "stream event expiry must be after occurrence");
        }

        @Override
        public String toString() {
            return "AppendCommand[tenantId=" + tenantId + ", streamType=" + streamType
                    + ", streamId=" + streamId + ", eventId=" + eventId + ", aggregateId=" + aggregateId
                    + ", aggregateVersion=" + aggregateVersion + ", type=" + type
                    + ", occurredAt=" + occurredAt + ", schemaVersion=" + schemaVersion
                    + ", correlationId=" + correlationId + ", dataKeys=" + data.keySet()
                    + ", expiresAt=" + expiresAt + "]";
        }
    }

    enum CursorStatus {
        VALID,
        EXPIRED,
        UNKNOWN_OR_FOREIGN
    }

    record ReplayEvent(DurableStreamEvent event, String cursor) {
        public ReplayEvent {
            DomainPreconditions.requireNonNull(event, "replayEvent");
            cursor = DomainPreconditions.requireText(cursor, "replayCursor");
            DomainPreconditions.require(cursor.length() <= 512, DomainErrorCode.INVALID_ARGUMENT,
                    "replay cursor exceeds maximum length");
        }
    }

    record RecoveryTarget(DurableStreamType streamType, ResourceId streamId) {
        public RecoveryTarget {
            DomainPreconditions.requireNonNull(streamType, "recoveryStreamType");
            DomainPreconditions.requireNonNull(streamId, "recoveryStreamId");
        }
    }

    record ReplayResult(
            CursorStatus status,
            List<ReplayEvent> events,
            Optional<String> nextCursor,
            boolean hasMore,
            Optional<RecoveryTarget> recoveryTarget
    ) {
        public ReplayResult {
            DomainPreconditions.requireNonNull(status, "cursorStatus");
            events = List.copyOf(events == null ? List.of() : events);
            nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
            nextCursor.ifPresent(value -> {
                DomainPreconditions.requireText(value, "nextStreamCursor");
                DomainPreconditions.require(value.length() <= 512, DomainErrorCode.INVALID_ARGUMENT,
                        "next stream cursor exceeds maximum length");
            });
            recoveryTarget = recoveryTarget == null ? Optional.empty() : recoveryTarget;
            if (status == CursorStatus.VALID) {
                DomainPreconditions.require(nextCursor.isPresent(), DomainErrorCode.INVALID_STATE,
                        "valid replay must preserve a cursor");
                DomainPreconditions.require(recoveryTarget.isEmpty(), DomainErrorCode.INVALID_STATE,
                        "valid replay cannot request snapshot recovery");
            } else {
                DomainPreconditions.require(events.isEmpty() && nextCursor.isEmpty() && !hasMore,
                        DomainErrorCode.INVALID_STATE, "invalid cursor result cannot contain replay events");
                DomainPreconditions.require((status == CursorStatus.EXPIRED) == recoveryTarget.isPresent(),
                        DomainErrorCode.INVALID_STATE, "only expired cursor exposes a recovery target");
            }
        }

        public static ReplayResult valid(List<ReplayEvent> events, String nextCursor, boolean hasMore) {
            return new ReplayResult(CursorStatus.VALID, events, Optional.of(nextCursor), hasMore, Optional.empty());
        }

        public static ReplayResult expired(DurableStreamType streamType, ResourceId streamId) {
            return new ReplayResult(CursorStatus.EXPIRED, List.of(), Optional.empty(), false,
                    Optional.of(new RecoveryTarget(streamType, streamId)));
        }

        public static ReplayResult unknownOrForeign() {
            return new ReplayResult(CursorStatus.UNKNOWN_OR_FOREIGN, List.of(), Optional.empty(), false,
                    Optional.empty());
        }
    }
}
