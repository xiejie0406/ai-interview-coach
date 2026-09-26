package com.ruoyi.fashion.application.selection;

public record SelectionReplaceCommand(
        String slotCode,
        String candidateRef,
        long quoteRowVersion,
        long comboRowVersion,
        String comboVisualHash) {
}
