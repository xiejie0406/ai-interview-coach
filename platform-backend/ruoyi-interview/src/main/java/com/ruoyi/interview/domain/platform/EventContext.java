package com.ruoyi.interview.domain.platform;

import java.time.Instant;

/** 命令进入领域时传入的非敏感事件上下文。 */
public record EventContext(CorrelationId correlationId, Instant occurredAt) {

    public EventContext {
        DomainPreconditions.requireNonNull(correlationId, "correlationId");
        DomainPreconditions.requireNonNull(occurredAt, "occurredAt");
    }
}
