package com.ruoyi.fashion.application.delivery;

import java.time.Instant;
import java.util.List;

/** 已持久化的交付产物元数据；对象内容不进入 MySQL。 */
public record DeliveryArtifact(
        String fileName, String objectKey, String contentType, String sha256, long byteSize,
        Integer pageCount, String role, List<String> skuCodes, String sourceMode,
        String reviewStatus, Instant retainUntil) {
}
