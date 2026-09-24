package com.ruoyi.fashion.application.selection;

import java.util.List;

public record SelectionComboView(
        String id,
        String quoteId,
        String comboNo,
        String name,
        int categoryCount,
        int setQty,
        boolean selected,
        int sortNo,
        String reason,
        List<String> lockedSlots,
        String visualHash,
        long rowVersion,
        List<SelectionDetailView> details) {
}
