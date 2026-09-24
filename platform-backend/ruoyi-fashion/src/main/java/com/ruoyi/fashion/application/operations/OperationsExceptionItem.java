package com.ruoyi.fashion.application.operations;

import java.time.Instant;

public record OperationsExceptionItem(
        String type, String id, String correlationId, String status,
        Instant occurredAt, String errorSummary) {
}
