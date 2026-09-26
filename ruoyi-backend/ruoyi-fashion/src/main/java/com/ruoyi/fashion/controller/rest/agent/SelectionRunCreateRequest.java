package com.ruoyi.fashion.controller.rest.agent;

import com.ruoyi.fashion.application.agent.run.SelectionRunCommand;

public final class SelectionRunCreateRequest {
    public String quoteId;
    public String requestKey;
    public String baseComboId;
    public String comboVisualHash;

    public SelectionRunCommand toCommand() {
        return new SelectionRunCommand(quoteId, requestKey, baseComboId, comboVisualHash);
    }
}
