package com.ruoyi.fashion.application.quote.pricing;

import java.math.BigDecimal;
import java.util.List;

public record QuotePricingCommand(
        String mode,
        String taxMode,
        BigDecimal taxRate,
        boolean feeTaxable,
        String discountType,
        BigDecimal discountRate,
        BigDecimal fixedDiscount,
        BigDecimal freight,
        int validDays,
        String publicNote,
        List<String> selectedComboIds,
        List<Line> lines,
        long rowVersion) {
    public record Line(String detailId, int qty, BigDecimal quotePrice) {}
}
