package com.ruoyi.fashion.application.agent.run;

public record RequirementRunCommand(
        String quoteId,
        String sourceText,
        String requestKey) {
}
