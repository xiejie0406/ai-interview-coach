package com.ruoyi.fashion.application.importing.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ruoyi.fashion.domain.importing.FashionImportBatch;
import com.ruoyi.fashion.domain.importing.FashionImportDetail;

public interface FashionImportRepository {
    Optional<FashionImportBatch> findBatchByRequestKey(String requestKey);

    Optional<FashionImportBatch> findBatchById(long id);

    List<FashionImportDetail> findDetails(long batchId);

    void insertBatch(FashionImportBatch batch);

    void insertDetails(List<FashionImportDetail> details);

    boolean markPublishing(long batchId, long expectedVersion, long operatorId, Instant now);

    void markDetailApplied(long detailId, Long productId, java.util.Map<String, Object> afterData, long operatorId, Instant now);

    void markBatchSuccess(long batchId, long operatorId, Instant now);

    void markBatchConflict(long batchId, long operatorId, Instant now, String reason);
}
