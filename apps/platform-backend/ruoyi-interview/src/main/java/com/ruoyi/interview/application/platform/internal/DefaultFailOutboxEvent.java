package com.ruoyi.interview.application.platform.internal;

import com.ruoyi.interview.application.platform.FailOutboxEvent;
import com.ruoyi.interview.application.platform.port.OutboxPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

public final class DefaultFailOutboxEvent implements FailOutboxEvent {

    private final OutboxPort outbox;
    private final TransactionPort transaction;

    public DefaultFailOutboxEvent(OutboxPort outbox, TransactionPort transaction) {
        this.outbox = java.util.Objects.requireNonNull(outbox);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public void handle(Command command) {
        transaction.required(() -> {
            var event = outbox.find(command.tenantId(), command.eventId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "outbox event was not found", false, Map.of()));
            event.fail(command.publisherId(), command.failedAt(), command.errorCode(), command.disposition(),
                    command.retryAt().orElse(null), command.expectedVersion());
            outbox.save(event);
        });
    }
}
