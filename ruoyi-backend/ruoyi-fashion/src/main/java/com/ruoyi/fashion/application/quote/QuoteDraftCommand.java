package com.ruoyi.fashion.application.quote;

import java.math.BigDecimal;
import java.util.List;

public record QuoteDraftCommand(
        String customerId,
        String title,
        String requirementText,
        RequirementFields requirements,
        int requestedQty,
        BigDecimal budget,
        String budgetBasis,
        String quoteMode,
        boolean progressive,
        List<QuoteTier> tiers,
        String warehouseCode,
        long rowVersion) {
}
