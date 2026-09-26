package com.ruoyi.fashion.application.quote.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/** 报价三层记录与当前商品/库存事实的单次读取结果。 */
public record QuotePricingState(
        long quoteId, String quoteNo, int versionNo, long customerId, String customerName, int requestedQty, String mode,
        String warehouseCode, String currency, String taxMode, BigDecimal taxRate, boolean feeTaxable,
        String discountType, BigDecimal discountRate, BigDecimal fixedDiscount, BigDecimal freight,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal totalAmount,
        JsonNode approval, int validDays, Instant validUntil, String publicNote, String contentHash,
        String status, long rowVersion, List<Combo> combos) {

    public record Combo(long id, String comboNo, String name, int categoryCount, int setQty, boolean selected,
            boolean allocationConfirmed, Long selectedImageId, Integer selectedImageNo, String visualHash,
            String selectedImageHash, boolean selectedImageValid,
            BigDecimal subtotal, BigDecimal discountAmount, BigDecimal freight, BigDecimal taxAmount,
            BigDecimal totalAmount, long rowVersion, List<Line> lines) {}

    public record Line(long id, String slotCode, long productId, String skuCode, String productName,
            String categoryCode, String colorName, String sizeCode, String unit, int qty,
            BigDecimal frozenSourcePrice, BigDecimal quotePrice, BigDecimal amount, Long frozenPriceBatchId,
            BigDecimal currentPrice, String currentCurrency, String currentTaxMode, Instant currentPriceAsOf,
            Long currentPriceBatchId, long currentProductRowVersion, String currentStatus,
            Integer frozenStockQty, Instant frozenStockAsOf, Long frozenStockBatchId,
            Integer currentStockQty, Instant currentStockAsOf, Long currentStockBatchId, long currentStockRowVersion,
            String frozenImageKey, String frozenImageHash, Long frozenImageVersion,
            String currentImageKey, String currentImageHash, boolean currentImageAllowed,
            long currentVisualVersion, long rowVersion) {}
}
