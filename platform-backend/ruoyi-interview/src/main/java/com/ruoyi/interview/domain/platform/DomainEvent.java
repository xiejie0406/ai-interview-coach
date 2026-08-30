package com.ruoyi.interview.domain.platform;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 领域事实的最小信封。attributes 只能放 allowlist 的 ID、状态、版本、计数或原因码，不能放正文。
 */
public interface DomainEvent {

    UUID eventId();

    String eventType();

    TenantId tenantId();

    ResourceId aggregateId();

    AggregateVersion aggregateVersion();

    CorrelationId correlationId();

    Instant occurredAt();

    Map<String, String> attributes();
}
