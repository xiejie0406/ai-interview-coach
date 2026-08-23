package com.aiinterviewcoach.domain.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 无框架的聚合事件缓冲区。应用层必须在同一本地事务中保存聚合并把事件追加到 Outbox；
 * pull 会清空内存缓冲，因此事务失败后必须丢弃/重载该聚合实例，不能把已清空实例继续复用。
 */
public abstract class AggregateRoot implements DomainEventSource {

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    protected final void recordEvent(
            String eventType,
            TenantId tenantId,
            ResourceId aggregateId,
            AggregateVersion aggregateVersion,
            EventContext context,
            Map<String, String> attributes
    ) {
        pendingEvents.add(RecordedDomainEvent.create(
                eventType, tenantId, aggregateId, aggregateVersion, context, attributes));
    }

    @Override
    public final List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> result = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return result;
    }
}
