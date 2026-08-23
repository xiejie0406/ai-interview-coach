package com.aiinterviewcoach.domain.platform;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 默认的脱敏领域事件实现。 */
public record RecordedDomainEvent(
        UUID eventId,
        String eventType,
        TenantId tenantId,
        ResourceId aggregateId,
        AggregateVersion aggregateVersion,
        CorrelationId correlationId,
        Instant occurredAt,
        Map<String, String> attributes
) implements DomainEvent {

    public RecordedDomainEvent {
        DomainPreconditions.requireNonNull(eventId, "eventId");
        eventType = DomainPreconditions.requireText(eventType, "eventType");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(aggregateId, "aggregateId");
        DomainPreconditions.requireNonNull(aggregateVersion, "aggregateVersion");
        DomainPreconditions.requireNonNull(correlationId, "correlationId");
        DomainPreconditions.requireNonNull(occurredAt, "occurredAt");
        attributes = Map.copyOf(new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
        attributes.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "event attribute key");
            DomainPreconditions.requireText(value, "event attribute value");
        });
    }

    public static RecordedDomainEvent create(
            String eventType,
            TenantId tenantId,
            ResourceId aggregateId,
            AggregateVersion aggregateVersion,
            EventContext context,
            Map<String, String> attributes
    ) {
        DomainPreconditions.requireNonNull(context, "eventContext");
        return new RecordedDomainEvent(
                UUID.randomUUID(),
                eventType,
                tenantId,
                aggregateId,
                aggregateVersion,
                context.correlationId(),
                context.occurredAt(),
                attributes
        );
    }
}
