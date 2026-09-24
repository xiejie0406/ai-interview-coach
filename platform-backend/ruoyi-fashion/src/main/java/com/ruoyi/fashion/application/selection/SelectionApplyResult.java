package com.ruoyi.fashion.application.selection;

import java.util.List;

public record SelectionApplyResult(
        String quoteId,
        long quoteRowVersion,
        List<SelectionComboView> combinations) {
}
