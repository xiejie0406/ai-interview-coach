package com.ruoyi.fashion.application.operations.port;

import java.time.Instant;
import java.util.List;

import com.ruoyi.fashion.application.operations.OperationsExceptionItem;
import com.ruoyi.fashion.application.operations.RetentionCandidate;

public interface FashionOperationsRepository {
    long countImports(Instant since);
    long countFailedImports(Instant since);
    long countImageFailedOrUnknown();
    long countExpiredStock(Instant cutoff);
    long countDeliveryTasks(Instant since);
    long countFailedDeliveryTasks(Instant since);
    double averageDeliveryDurationMs(Instant since);
    List<OperationsExceptionItem> exceptions(Instant now, int limit);
    List<RetentionCandidate> retentionCandidates(
            Instant now, Instant quoteCutoff, Instant importCutoff, Instant failedImageCutoff);
}
