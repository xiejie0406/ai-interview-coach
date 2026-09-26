package com.ruoyi.fashion.application.selection;

import java.math.BigDecimal;
import java.time.Instant;

public record SelectionDetailWrite(
        long id,
        long comboId,
        Long aiRunId,
        int lineNo,
        String slotCode,
        long productId,
        String sourceCode,
        String skuCode,
        String styleCode,
        String productName,
        String categoryCode,
        String colorCode,
        String colorName,
        String sizeCode,
        String sizeSystem,
        String unit,
        int qty,
        BigDecimal sourcePrice,
        long priceBatchId,
        long stockBatchId,
        int stockQty,
        Instant stockAsOf,
        String imageKey,
        String imageHash,
        long imageVersion,
        long operatorId,
        Instant now) {
}
