package com.ruoyi.fashion.application.agent.run.port;

import java.time.Instant;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.agent.AgentVersionView;
import com.ruoyi.fashion.application.agent.run.RunView;
import com.ruoyi.fashion.application.agent.run.RunWorkItem;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.application.product.ProductView;

public interface FashionRunRepository {
    Optional<RunView> findById(long id);

    Optional<RunView> findByRunNo(String runNo);

    Optional<RunView> findByRequestKey(String requestKey);

    RunView insertRequirementRun(
            long conversationId,
            long messageId,
            long runId,
            FashionQuote quote,
            AgentVersionView version,
            String sourceText,
            String sourceHash,
            String requestKey,
            Instant deadlineAt,
            long operatorId,
            Instant now);

    RunView insertProductAttributeRun(
            long conversationId,
            long messageId,
            long runId,
            ProductView product,
            AgentVersionView version,
            String sourceHash,
            String requestKey,
            Instant deadlineAt,
            long operatorId,
            Instant now);

    RunView insertSelectionRun(
            long conversationId,
            long messageId,
            long runId,
            FashionQuote quote,
            AgentVersionView version,
            JsonNode selectionSnapshot,
            String sourceHash,
            String requestKey,
            Instant deadlineAt,
            long operatorId,
            Instant now);

    Optional<RunWorkItem> claimNext(
            long stepId, String leaseId, String owner, Instant now, Instant leaseUntil);

    int expireDue(Instant now);

    boolean renew(long runId, String leaseId, long fencingToken, Instant now, Instant leaseUntil);

    boolean complete(
            RunWorkItem item, JsonNode result, String outputType, String outputHash,
            long assistantMessageId, String assistantSummary, Instant now);

    boolean fail(RunWorkItem item, String errorCode, String errorMessage, boolean retryable, Instant now);

    boolean acknowledgeCancellation(RunWorkItem item, Instant now);

    boolean cancel(long runId, long expectedRowVersion, long operatorId, Instant now);

    boolean markApplied(
            long runId, String applyRequestKey, String applyInputHash, long operatorId,
            JsonNode appliedResult, Instant now);
}
