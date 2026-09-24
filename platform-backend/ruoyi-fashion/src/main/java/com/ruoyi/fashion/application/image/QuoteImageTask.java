package com.ruoyi.fashion.application.image;

import java.math.BigDecimal;
import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** 一行 fq_quote_image 的受限业务视图。 */
public record QuoteImageTask(
        long id,
        long quoteId,
        long comboId,
        Long aiRunId,
        Long sourceImageId,
        String imageType,
        String sourceMode,
        String inputHash,
        JsonNode inputs,
        JsonNode parameters,
        int requestedCount,
        JsonNode results,
        String providerCode,
        String providerTaskId,
        String requestKey,
        String status,
        boolean stale,
        int retryCount,
        Instant nextRetryAt,
        Instant leaseUntil,
        BigDecimal estimatedCost,
        BigDecimal actualCost,
        String costCurrency,
        String billingStatus,
        JsonNode billingEvents,
        String errorMessage,
        Instant finishedAt,
        Instant createTime,
        boolean adopted,
        Integer adoptedResultNo,
        long rowVersion) {
}
