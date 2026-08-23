package com.aiinterviewcoach.application.platform.internal;

import com.aiinterviewcoach.application.platform.PublishOutboxEvent;
import com.aiinterviewcoach.application.platform.port.OutboxPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** 仅在外部 publisher 已成功发送并回读确认后调用；本用例本身不猜测外部发送结果。 */
public final class DefaultPublishOutboxEvent implements PublishOutboxEvent {

    private final OutboxPort outbox;
    private final TransactionPort transaction;

    public DefaultPublishOutboxEvent(OutboxPort outbox, TransactionPort transaction) {
        this.outbox = java.util.Objects.requireNonNull(outbox);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public void handle(Command command) {
        transaction.required(() -> {
            var event = outbox.find(command.tenantId(), command.eventId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "outbox event was not found", false, Map.of()));
            event.publish(command.publisherId(), command.publishedAt(), command.expectedVersion());
            outbox.save(event);
        });
    }
}
