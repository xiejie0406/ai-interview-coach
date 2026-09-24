package com.ruoyi.fashion.application.image;

import java.math.BigDecimal;
import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record QuoteImageView(
        String id,
        String quoteId,
        String comboId,
        String imageType,
        String sourceMode,
        String sourceLabel,
        String inputHash,
        JsonNode inputs,
        JsonNode parameters,
        int requestedCount,
        JsonNode results,
        String providerCode,
        String providerTaskId,
        String status,
        String displayStatus,
        boolean stale,
        int retryCount,
        BigDecimal estimatedCost,
        BigDecimal actualCost,
        String costCurrency,
        String billingStatus,
        String errorMessage,
        Instant finishedAt,
        Instant createTime,
        long rowVersion) {
}
