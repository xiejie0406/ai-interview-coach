package com.ruoyi.fashion.domain.quote;

import java.math.BigDecimal;
import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** 报价方案主表事实；IMP-05 只编辑草稿与需求字段。 */
public record FashionQuote(
        long id,
        String quoteNo,
        int versionNo,
        Long sourceQuoteId,
        long customerId,
        String customerName,
        String title,
        long salespersonId,
        String requirementText,
        JsonNode requirementJson,
        boolean requirementConfirmed,
        int requestedQty,
        BigDecimal budget,
        String budgetBasis,
        String quoteMode,
        boolean progressive,
        JsonNode comboTemplateJson,
        String warehouseCode,
        String currency,
        String taxMode,
        JsonNode presentationJson,
        String status,
        long createBy,
        Instant createTime,
        long updateBy,
        Instant updateTime,
        long rowVersion) {
}
