package com.ruoyi.aden.domain.runner;

import java.time.Instant;
import java.util.Objects;

public record AdenRunnerReceipt(
        AdenDeliveryId deliveryId,
        AdenSessionId sessionId,
        long sessionEpoch,
        AdenFencingToken fencingToken,
        long receiptSequence,
        AdenReceiptType type,
        String payloadJson,
        Instant occurredAt) {
    public AdenRunnerReceipt {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionEpoch < 1 || receiptSequence < 1) throw new IllegalArgumentException("receipt 序号非法");
        Objects.requireNonNull(fencingToken, "fencingToken");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
