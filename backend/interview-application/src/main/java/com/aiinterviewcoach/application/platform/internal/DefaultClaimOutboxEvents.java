package com.aiinterviewcoach.application.platform.internal;

import com.aiinterviewcoach.application.platform.ClaimOutboxEvents;
import com.aiinterviewcoach.application.platform.port.OutboxPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;

import java.util.ArrayList;

public final class DefaultClaimOutboxEvents implements ClaimOutboxEvents {

    private final OutboxPort outbox;
    private final TransactionPort transaction;

    public DefaultClaimOutboxEvents(OutboxPort outbox, TransactionPort transaction) {
        this.outbox = java.util.Objects.requireNonNull(outbox);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public java.util.List<ClaimedEvent> handle(Command command) {
        return transaction.required(() -> {
            var result = new ArrayList<ClaimedEvent>();
            for (var event : outbox.findPublishable(command.now(), command.limit())) {
                event.claim(command.publisherId(), command.now(), command.leaseDuration(), event.version());
                outbox.save(event);
                result.add(new ClaimedEvent(event.tenantId(), event.id(), event.aggregateType(), event.aggregateId(),
                        event.sourceAggregateVersion(), event.eventType(), event.schemaVersion(),
                        event.correlationId(), event.payloadReferences(), event.attemptCount(), event.version(),
                        event.claimExpiresAt().orElseThrow()));
            }
            return result;
        });
    }
}
