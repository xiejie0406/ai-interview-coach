package com.ruoyi.fashion.application.image.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.image.QuoteImageTask;

public interface FashionQuoteImageRepository {
    List<QuoteImageTask> findByQuoteId(long quoteId);

    Optional<QuoteImageTask> findById(long imageId);

    Optional<QuoteImageTask> findByRequestKey(String requestKey);

    JsonNode currentInputs(long comboId);

    void insert(QuoteImageTask task, long operatorId, Instant now);

    Optional<QuoteImageTask> claimNext(String workerId, Instant now, Instant leaseUntil);

    boolean updateExecution(long imageId, long fencingVersion, String status, JsonNode results,
            String providerCode, String providerTaskId, int retryCount, Instant nextRetryAt,
            BigDecimal actualCost, String billingStatus, JsonNode billingEvents,
            String errorMessage, Instant finishedAt, Instant now);

    boolean requestCancel(long imageId, long expectedRowVersion, long operatorId, Instant now);

    boolean updateReview(long imageId, JsonNode results, long expectedRowVersion, long operatorId, Instant now);

    boolean markStale(long imageId, long expectedRowVersion, long operatorId, Instant now);

    boolean adopt(long comboId, long imageId, int resultNo, String inputHash,
            long expectedImageVersion, long operatorId, Instant now);

    BigDecimal settledCostSince(Instant since);

    BigDecimal committedCostSince(Instant since);

    void lockMonthlyBudget();
}
