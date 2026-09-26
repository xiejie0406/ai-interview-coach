package com.ruoyi.fashion.application.quote.pricing;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Issue;

public record QuotePricingWorkspace(
        String quoteId, String quoteNo, int versionNo, String customerName, int requestedQty, String status, long rowVersion,
        String mode, String warehouseCode, String currency, String taxMode, String taxRate,
        boolean feeTaxable, String discountType, String discountRate, String fixedDiscount,
        String freight, int validDays, Instant validUntil, String publicNote, String inputHash,
        boolean approvalRequired, boolean approvalValid, JsonNode approval, String subtotal,
        String discountAmount, String taxAmount, String totalAmount, Integer maximumAvailableSets,
        List<CalculatedCombo> calculatedCombos, List<Issue> issues, List<Combo> combos) {

    public record CalculatedCombo(String id, String subtotal, String discountAmount, String freight,
            String taxAmount, String totalAmount, String averagePerSet, Integer maximumAvailableSets) {}

    public record Combo(String id, String comboNo, String name, int categoryCount, int setQty, boolean selected,
            boolean allocationConfirmed, String selectedImageId, Integer selectedImageNo,
            boolean selectedImageValid,
            String subtotal, String discountAmount, String freight, String taxAmount,
            String totalAmount, long rowVersion, List<Line> lines) {}
    public record Line(String id, String slotCode, String productId, String skuCode, String productName,
            String categoryCode, String colorName, String sizeCode, String unit, int qty,
            String sourcePrice, String quotePrice, String amount, Integer stockQty,
            Instant stockAsOf, String priceBatchId, String stockBatchId, boolean priceChanged,
            boolean stockChanged, boolean imageChanged, long rowVersion) {}
}
