package com.ruoyi.fashion.application.image;

import java.math.BigDecimal;
import java.util.List;

import com.ruoyi.fashion.application.selection.SelectionComboView;

public record QuoteImageWorkspace(
        String quoteId,
        long quoteRowVersion,
        String quoteStatus,
        boolean providerEnabled,
        String providerReason,
        BigDecimal monthlyBudget,
        BigDecimal monthSettledCost,
        List<SelectionComboView> combinations,
        List<QuoteImageView> tasks) {
}
