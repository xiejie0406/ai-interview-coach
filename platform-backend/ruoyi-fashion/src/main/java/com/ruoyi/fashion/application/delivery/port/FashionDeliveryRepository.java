package com.ruoyi.fashion.application.delivery.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ruoyi.fashion.application.delivery.DeliveryArtifact;
import com.ruoyi.fashion.application.delivery.DeliveryFileTask;
import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;

public interface FashionDeliveryRepository {
    Optional<DeliveryQuoteSnapshot> findConfirmedSnapshot(long quoteId);
    List<DeliveryFileTask> findByQuote(long quoteId);
    Optional<DeliveryFileTask> findById(long id);
    Optional<DeliveryFileTask> findByRequestKey(String requestKey);
    boolean insert(DeliveryFileTask task);
    Optional<DeliveryFileTask> claimNext(Instant now, Instant leaseUntil);
    boolean complete(long id, long expectedRowVersion, List<DeliveryArtifact> artifacts, long operatorId, Instant now);
    boolean fail(long id, long expectedRowVersion, int retryCount, Instant nextRetryAt,
            String errorMessage, long operatorId, Instant now);
    boolean requeue(long id, long expectedRowVersion, long operatorId, Instant now);
    boolean markDownloaded(long id, long expectedRowVersion, long operatorId, Instant now);
    boolean extendRetention(long id, long expectedRowVersion, Instant retainUntil, long operatorId, Instant now);
}
