package com.ruoyi.fashion.controller.rest.quote;

import java.math.BigDecimal;
import java.util.List;

import com.ruoyi.fashion.application.quote.pricing.QuotePricingCommand;

public record QuotePricingRequest(String mode, String taxMode, BigDecimal taxRate, boolean feeTaxable,
        String discountType, BigDecimal discountRate, BigDecimal fixedDiscount, BigDecimal freight,
        int validDays, String publicNote, List<String> selectedComboIds, List<Line> lines, long rowVersion) {
    QuotePricingCommand toCommand() {
        return new QuotePricingCommand(mode, taxMode, taxRate, feeTaxable, discountType, discountRate,
                fixedDiscount, freight, validDays, publicNote, selectedComboIds,
                lines == null ? List.of() : lines.stream()
                        .map(line -> new QuotePricingCommand.Line(line.detailId(), line.qty(), line.quotePrice()))
                        .toList(), rowVersion);
    }
    public record Line(String detailId, int qty, BigDecimal quotePrice) {}
}
