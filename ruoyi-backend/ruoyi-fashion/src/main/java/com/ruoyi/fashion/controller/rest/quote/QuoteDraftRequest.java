package com.ruoyi.fashion.controller.rest.quote;

import java.math.BigDecimal;
import java.util.List;

import com.ruoyi.fashion.application.quote.QuoteDraftCommand;

public class QuoteDraftRequest {
    public String customerId;
    public String title;
    public String requirementText;
    public RequirementFieldsRequest requirements;
    public int requestedQty;
    public BigDecimal budget;
    public String budgetBasis;
    public String quoteMode;
    public boolean progressive;
    public List<QuoteTierRequest> tiers;
    public String warehouseCode;
    public long rowVersion;

    QuoteDraftCommand toCommand() {
        return new QuoteDraftCommand(customerId, title, requirementText,
                requirements == null ? null : requirements.toCommand(), requestedQty, budget, budgetBasis,
                quoteMode, progressive, tiers == null ? List.of() : tiers.stream().map(QuoteTierRequest::toCommand).toList(),
                warehouseCode, rowVersion);
    }
}
