package com.ruoyi.fashion.domain.importing;

import java.time.Instant;
import java.util.Map;

public record FashionImportBatch(
        long id,
        String batchNo,
        String templateCode,
        String templateVersion,
        String importType,
        String operationType,
        String sourceCode,
        String fileName,
        String fileKey,
        String fileHash,
        Map<String, String> mappingSnapshot,
        Map<String, Object> scope,
        String scopeHash,
        String baseDataHash,
        Instant asOf,
        String requestKey,
        int expectedCount,
        int actualCount,
        int errorCount,
        String status,
        Instant validatedAt,
        Instant publishedAt,
        String errorMessage,
        long createBy,
        Instant createTime,
        long updateBy,
        Instant updateTime,
        long rowVersion,
        Long sourceBatchId,
        String warehouseCode,
        String operatorNote) {
}
