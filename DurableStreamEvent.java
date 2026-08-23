package com.aiinterviewcoach.domain.platform;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可回放的脱敏事件事实。data 只能包含公开 allowlist 中的 ID、版本、状态、计数或原因码；
 * 回答、转写、Prompt、模型信息、支付信息和对象存储 key 必须留在各自 owner 中。
 */
public record DurableStreamEvent(
        TenantId tenantId,
        DurableStreamType streamType,
        ResourceId streamId,
        ResourceId eventId,
        ResourceId aggregateId,
        AggregateVersion aggregateVersion,
        long sequence,
        String type,
        Instant occurredAt,
        int schemaVersion,
        CorrelationId correlationId,
        Map<String, String> data,
        Instant expiresAt
) {
    public DurableStreamEvent {
        DomainPreconditions.requireNonNull(tenantId, "streamTenantId");
        DomainPreconditions.requireNonNull(streamType, "streamType");
        DomainPreconditions.requireNonNull(streamId, "streamId");
        DomainPreconditions.requireNonNull(eventId, "streamEventId");
        DomainPreconditions.requireNonNull(aggregateId, "streamAggregateId");
        DomainPreconditions.requireNonNull(aggregateVersion, "streamAggregateVersion");
        DomainPreconditions.require(sequence > 0, DomainErrorCode.INVALID_ARGUMENT,
                "stream sequence must be positive");
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
        return "DurableStreamEvent[tenantId=" + tenantId + ", streamType=" + streamType
                + ", streamId=" + streamId + ", eventId=" + eventId + ", aggregateId=" + aggregateId
                + ", aggregateVersion=" + aggregateVersion + ", sequence=" + sequence + ", type=" + type
                + ", occurredAt=" + occurredAt + ", schemaVersion=" + schemaVersion
                + ", correlationId=" + correlationId + ", dataKeys=" + data.keySet()
                + ", expiresAt=" + expiresAt + "]";
    }
}
