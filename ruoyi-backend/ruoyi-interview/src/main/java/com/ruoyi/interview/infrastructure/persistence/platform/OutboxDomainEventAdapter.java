package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.application.platform.port.OutboxPort;
import com.ruoyi.interview.domain.platform.DomainEvent;
import com.ruoyi.interview.domain.platform.OutboxEvent;
import com.ruoyi.interview.domain.platform.ResourceId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@InterviewEnabled
@Repository
public class OutboxDomainEventAdapter implements DomainEventPort {

    private final DomainEventEnvelopePolicy envelopes;
    private final OutboxPort outbox;
    private final DurableStreamEventPolicy streamEvents;
    private final DurableStreamPort durableStreams;

    public OutboxDomainEventAdapter(
            DomainEventEnvelopePolicy envelopes,
            OutboxPort outbox,
            DurableStreamEventPolicy streamEvents,
            DurableStreamPort durableStreams
    ) {
        this.envelopes = java.util.Objects.requireNonNull(envelopes);
        this.outbox = java.util.Objects.requireNonNull(outbox);
        this.streamEvents = java.util.Objects.requireNonNull(streamEvents);
        this.durableStreams = java.util.Objects.requireNonNull(durableStreams);
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public void append(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            DomainEventEnvelopePolicy.Envelope envelope = envelopes.classify(event);
            outbox.save(new OutboxEvent(ResourceId.of(event.eventId()), event.tenantId(),
                    envelope.aggregateType(), event.aggregateId(), event.aggregateVersion(),
                    event.eventType(), envelope.schemaVersion(), event.correlationId(),
                    envelope.payloadReferences(), event.occurredAt()));
            // 与业务聚合和 Outbox 共用调用方的本地事务；任何未注册/写入失败都会整体回滚。
            streamEvents.classify(event).ifPresent(durableStreams::append);
        }
    }
}

