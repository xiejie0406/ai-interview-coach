package com.ruoyi.fashion.application.stock.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import com.ruoyi.fashion.domain.stock.FashionStock;

public interface FashionStockRepository {
    Map<Long, FashionStock> findByProductIds(Collection<Long> productIds, String warehouseCode);

    void insert(FashionStock stock);

    boolean update(
            long id,
            int availableQty,
            Instant asOf,
            String confirmationType,
            Long verifiedBy,
            String evidenceNote,
            long batchId,
            long expectedRowVersion,
            long operatorId,
            Instant now);

    boolean delete(long id, long expectedRowVersion);
}
