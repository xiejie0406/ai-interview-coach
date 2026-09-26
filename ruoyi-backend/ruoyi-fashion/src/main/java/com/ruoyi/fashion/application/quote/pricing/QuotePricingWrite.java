package com.ruoyi.fashion.application.quote.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

public record QuotePricingWrite(
        long quoteId, long expectedRowVersion, String mode, String taxMode, BigDecimal taxRate,
        boolean feeTaxable, String discountType, BigDecimal discountRate, BigDecimal fixedDiscount,
        BigDecimal freight, int validDays, String publicNote, BigDecimal subtotal, BigDecimal discountAmount,
        BigDecimal taxAmount, BigDecimal totalAmount, String contentHash, JsonNode approval,
        List<Combo> combos, long operatorId, Instant now) {
    public record Combo(long id, boolean selected, boolean allocationConfirmed, BigDecimal subtotal,
            BigDecimal discountAmount, BigDecimal freight, BigDecimal taxAmount, BigDecimal totalAmount,
            List<Line> lines) {}
    public record Line(long id, int qty, BigDecimal sourcePrice, BigDecimal quotePrice, BigDecimal amount,
            Long priceBatchId, Integer stockQty, Instant stockAsOf, Long stockBatchId,
            String imageKey, String imageHash, Long imageVersion) {}
}
