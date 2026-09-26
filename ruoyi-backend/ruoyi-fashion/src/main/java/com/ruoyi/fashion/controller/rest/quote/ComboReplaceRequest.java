package com.ruoyi.fashion.controller.rest.quote;

import com.ruoyi.fashion.application.selection.SelectionReplaceCommand;

public final class ComboReplaceRequest {
    public String slotCode;
    public String candidateRef;
    public long quoteRowVersion;
    public long comboRowVersion;
    public String comboVisualHash;

    public SelectionReplaceCommand toCommand() {
        return new SelectionReplaceCommand(slotCode, candidateRef, quoteRowVersion, comboRowVersion, comboVisualHash);
    }
}
