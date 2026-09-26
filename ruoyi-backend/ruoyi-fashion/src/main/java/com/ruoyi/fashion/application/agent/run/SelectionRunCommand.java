package com.ruoyi.fashion.application.agent.run;

public record SelectionRunCommand(
        String quoteId,
        String requestKey,
        String baseComboId,
        String comboVisualHash) {
}
