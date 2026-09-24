package com.ruoyi.fashion.application.selection;

import java.math.BigDecimal;
import java.time.Instant;

public record SelectionDetailView(
        String id,
        int lineNo,
        String slotCode,
        String productId,
        String sourceCode,
        String skuCode,
        String styleCode,
        String productName,
        String categoryCode,
        String colorCode,
        String colorName,
        String sizeCode,
        int qty,
        BigDecimal sourcePrice,
        Integer stockQty,
        Instant stockAsOf,
        String imageKey,
        String imageHash,
        Long imageVersion,
        long rowVersion) {
}
