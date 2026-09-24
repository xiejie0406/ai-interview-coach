package com.ruoyi.fashion.application.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.domain.quote.FashionQuote;

public record QuoteView(
        String id,
        String quoteNo,
        int versionNo,
        String sourceQuoteId,
        String customerId,
        String customerName,
        String title,
        String salespersonId,
        String requirementText,
        JsonNode requirement,
        boolean requirementConfirmed,
        int requestedQty,
        BigDecimal budget,
        String budgetBasis,
        String quoteMode,
        boolean progressive,
        JsonNode comboTemplate,
        String warehouseCode,
        String currency,
        String taxMode,
        String status,
        Instant updateTime,
        long rowVersion,
        List<ProductPriceFact> priceFacts) {

    public static QuoteView from(FashionQuote quote, List<ProductPriceFact> priceFacts) {
        return new QuoteView(Long.toString(quote.id()), quote.quoteNo(), quote.versionNo(),
                quote.sourceQuoteId() == null ? null : Long.toString(quote.sourceQuoteId()),
                Long.toString(quote.customerId()), quote.customerName(), quote.title(),
                Long.toString(quote.salespersonId()), quote.requirementText(), quote.requirementJson(),
                quote.requirementConfirmed(), quote.requestedQty(), quote.budget(), quote.budgetBasis(),
                quote.quoteMode(), quote.progressive(), quote.comboTemplateJson(), quote.warehouseCode(),
                quote.currency(), quote.taxMode(), quote.status(), quote.updateTime(), quote.rowVersion(), priceFacts);
    }
}
