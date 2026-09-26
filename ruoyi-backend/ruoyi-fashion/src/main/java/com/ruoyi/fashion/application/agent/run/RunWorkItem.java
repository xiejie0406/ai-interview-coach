package com.ruoyi.fashion.application.agent.run;

import java.time.Instant;
import java.math.BigDecimal;

import tools.jackson.databind.JsonNode;

public record RunWorkItem(
        long runId,
        long stepId,
        String runNo,
        String triggerType,
        long conversationId,
        long agentVersionId,
        long agentId,
        String providerCode,
        String modelName,
        String configHash,
        Long quoteId,
        long quoteRowVersion,
        Long customerId,
        String customerCode,
        String sourceText,
        String sourceHash,
        JsonNode knownRequirements,
        int requestedQty,
        BigDecimal budget,
        String budgetBasis,
        boolean progressive,
        JsonNode comboTemplate,
        JsonNode contextSnapshot,
        Long productId,
        long productRowVersion,
        JsonNode productSnapshot,
        String requestKey,
        Instant deadlineAt,
        int runAttempt,
        String leaseId,
        String leaseOwner,
        long fencingToken,
        Instant leaseUntil,
        int stepNo) {
}
