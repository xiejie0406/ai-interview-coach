package com.aiinterviewcoach.application.platform.port;

import com.aiinterviewcoach.domain.platform.DomainEvent;

import java.util.List;

/** 在业务事务内把领域事件转换/保存为最小 Outbox；不直接调用外部 broker。 */
public interface DomainEventPort {

    void append(List<DomainEvent> events);
}
