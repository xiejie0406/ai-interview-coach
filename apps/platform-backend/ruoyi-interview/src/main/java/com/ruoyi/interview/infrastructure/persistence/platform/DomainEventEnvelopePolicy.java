package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.domain.platform.DomainEvent;

import java.util.Map;

/**
 * 领域事件到公开事件信封的显式版本与字段 allowlist。仓库不根据 eventType 字符串猜
 * aggregateType，不把所有事件静默锁成 schemaVersion=1，也不直接复制可能含正文的 attributes；
 * 缺少批准注册表时 DomainEventPort 无法装配。
 */
public interface DomainEventEnvelopePolicy {

    Envelope classify(DomainEvent event);

    record Envelope(String aggregateType, int schemaVersion, Map<String, String> payloadReferences) {
        public Envelope {
            if (aggregateType == null || aggregateType.isBlank()) {
                throw new IllegalArgumentException("aggregateType must not be blank");
            }
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            payloadReferences = Map.copyOf(payloadReferences == null ? Map.of() : payloadReferences);
        }
    }
}

