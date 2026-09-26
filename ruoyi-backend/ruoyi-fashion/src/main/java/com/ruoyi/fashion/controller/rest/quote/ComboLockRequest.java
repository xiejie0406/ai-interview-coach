package com.ruoyi.fashion.controller.rest.quote;

import java.util.List;

public final class ComboLockRequest {
    public List<String> lockedSlots = List.of();
    public long quoteRowVersion;
    public long comboRowVersion;
    public String visualHash;
}
