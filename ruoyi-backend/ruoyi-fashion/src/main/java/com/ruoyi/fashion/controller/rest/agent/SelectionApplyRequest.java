package com.ruoyi.fashion.controller.rest.agent;

import java.util.Map;

public final class SelectionApplyRequest {
    public String requestKey;
    public long quoteRowVersion;
    public Map<String, String> comboVisualHashes = Map.of();
}
