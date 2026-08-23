package com.aiinterviewcoach.domain.platform;

import java.util.List;

/** 允许应用层在同一事务中收集领域事件并写入 Outbox。 */
public interface DomainEventSource {

    List<DomainEvent> pullDomainEvents();
}
