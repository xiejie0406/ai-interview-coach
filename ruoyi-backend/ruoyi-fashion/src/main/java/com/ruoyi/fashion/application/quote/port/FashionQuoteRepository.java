package com.ruoyi.fashion.application.quote.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ruoyi.fashion.application.quote.ProductPriceFact;
import com.ruoyi.fashion.domain.quote.FashionQuote;

public interface FashionQuoteRepository {
    List<FashionQuote> search(long userId, boolean administrator, String status, String keyword, int offset, int limit);

    long count(long userId, boolean administrator, String status, String keyword);

    Optional<FashionQuote> findById(long id);

    void insert(FashionQuote quote);

    boolean updateDraft(FashionQuote quote, long expectedRowVersion);

    boolean updateStatus(long id, String status, long expectedRowVersion, long operatorId, Instant now);

    boolean touchDraft(long id, long expectedRowVersion, long operatorId, Instant now);

    int nextVersion(String quoteNo);

    List<ProductPriceFact> findPriceFacts(long quoteId);
}
