package com.ruoyi.fashion.application.quote;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductPriceFact(
        String productId,
        String skuCode,
        BigDecimal salePrice,
        String currency,
        String taxMode,
        Instant priceAsOf,
        String sourceBatchId,
        long rowVersion,
        boolean ready) {
}
