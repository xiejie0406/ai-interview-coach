package com.ruoyi.fashion.application.delivery;

public record DeliveryDownload(
        long taskId, long taskRowVersion, DeliveryArtifact artifact, byte[] content) {
}
