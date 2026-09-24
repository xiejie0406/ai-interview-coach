package com.ruoyi.fashion.application.delivery;

import java.time.Instant;
import java.util.List;

public record DeliveryFileView(
        String id, String fileType, String purpose, String rendererVersion, String requestKey,
        String status, List<DeliveryArtifact> files, int retryCount, Instant nextRetryAt,
        Instant leaseUntil, String errorMessage, Instant lastDownloadAt, int downloadCount,
        Instant createTime, long rowVersion) {

    public static DeliveryFileView from(DeliveryFileTask task) {
        return new DeliveryFileView(Long.toString(task.id()), task.fileType(), task.purpose(),
                task.rendererVersion(), task.requestKey(), task.status(), task.artifacts(),
                task.retryCount(), task.nextRetryAt(), task.leaseUntil(), task.errorMessage(),
                task.lastDownloadAt(), task.downloadCount(), task.createTime(), task.rowVersion());
    }
}
