package com.ruoyi.fashion.application.delivery;

import java.time.Instant;
import java.util.List;

public record DeliveryFileTask(
        long id, long quoteId, String quoteNo, int versionNo, String quoteTitle,
        String fileType, String purpose, String quoteHash, String rendererVersion,
        String requestKey, String status, List<DeliveryArtifact> artifacts, int retryCount,
        Instant nextRetryAt, Instant leaseUntil, String errorMessage, Instant lastDownloadAt,
        int downloadCount, long createBy, Instant createTime, long rowVersion) {
}
