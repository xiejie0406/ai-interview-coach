package com.ruoyi.fashion.application.delivery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/** 单一已确认报价版本的冻结交付输入。严禁渲染时用当前商品价覆盖此对象。 */
public record DeliveryQuoteSnapshot(
        long quoteId, String quoteNo, int versionNo, String title, String customerName,
        int requestedQty, String quoteMode, String warehouseCode, String currency,
        String taxMode, BigDecimal taxRate, String discountType, BigDecimal discountRate,
        BigDecimal fixedDiscount, BigDecimal freight, BigDecimal subtotal,
        BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal totalAmount,
        int validDays, Instant validUntil, String publicNote, String contentHash,
        Instant confirmedAt, JsonNode requirement, JsonNode presentation, List<Combo> combos) {

    public record Combo(
            long id, String comboNo, String name, int categoryCount, int setQty,
            BigDecimal subtotal, BigDecimal discountAmount, BigDecimal freight,
            BigDecimal taxAmount, BigDecimal totalAmount, ImageRef adoptedImage,
            List<Line> lines) {
    }

    public record Line(
            int lineNo, String slotCode, long productId, String skuCode, String styleCode,
            String productName, String categoryCode, String colorName, String sizeCode,
            String unit, int qty, BigDecimal sourcePrice, BigDecimal quotePrice,
            BigDecimal amount, Integer stockQty, Instant stockAsOf,
            String imageKey, String imageHash, String remark) {
    }

    public record ImageRef(
            String objectKey, String sha256, String sourceMode, String reviewStatus) {
    }
}
