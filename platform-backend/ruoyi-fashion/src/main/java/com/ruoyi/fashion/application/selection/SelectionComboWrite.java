package com.ruoyi.fashion.application.selection;

import java.time.Instant;
import java.util.List;

public record SelectionComboWrite(
        long id,
        long quoteId,
        String comboNo,
        String name,
        int categoryCount,
        int setQty,
        int sortNo,
        String reason,
        List<String> lockedSlots,
        String visualHash,
        long operatorId,
        Instant now) {
}
