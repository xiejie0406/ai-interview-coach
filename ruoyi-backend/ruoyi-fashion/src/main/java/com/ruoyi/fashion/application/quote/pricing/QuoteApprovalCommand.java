package com.ruoyi.fashion.application.quote.pricing;

public record QuoteApprovalCommand(String inputHash, String reason, long rowVersion) {}
