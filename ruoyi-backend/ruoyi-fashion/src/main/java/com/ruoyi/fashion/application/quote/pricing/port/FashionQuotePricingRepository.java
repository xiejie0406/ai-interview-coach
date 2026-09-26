package com.ruoyi.fashion.application.quote.pricing.port;

import java.time.Instant;
import java.util.Optional;
import java.util.function.LongSupplier;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingState;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingWrite;

public interface FashionQuotePricingRepository {
    Optional<QuotePricingState> find(long quoteId, boolean forUpdate);
    boolean saveDraft(QuotePricingWrite write);
    boolean updateApproval(long quoteId, JsonNode approval, String inputHash, long expectedRowVersion,
            long operatorId, Instant now);
    boolean confirm(QuotePricingWrite write, long confirmedBy, Instant validUntil);
    void copyStructure(long sourceQuoteId, long targetQuoteId, LongSupplier ids, long operatorId, Instant now);
}
