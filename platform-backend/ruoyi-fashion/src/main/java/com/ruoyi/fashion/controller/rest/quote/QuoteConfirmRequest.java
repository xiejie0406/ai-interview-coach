package com.ruoyi.fashion.controller.rest.quote;

import com.ruoyi.fashion.application.quote.pricing.QuoteConfirmCommand;

public record QuoteConfirmRequest(String inputHash, long rowVersion) {
    QuoteConfirmCommand toCommand() { return new QuoteConfirmCommand(inputHash, rowVersion); }
}
