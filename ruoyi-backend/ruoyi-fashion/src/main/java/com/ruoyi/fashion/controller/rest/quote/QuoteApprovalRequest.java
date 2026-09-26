package com.ruoyi.fashion.controller.rest.quote;

import com.ruoyi.fashion.application.quote.pricing.QuoteApprovalCommand;

public record QuoteApprovalRequest(String inputHash, String reason, long rowVersion) {
    QuoteApprovalCommand toCommand() { return new QuoteApprovalCommand(inputHash, reason, rowVersion); }
}
