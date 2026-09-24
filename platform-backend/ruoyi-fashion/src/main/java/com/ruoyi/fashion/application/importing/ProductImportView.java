package com.ruoyi.fashion.application.importing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ruoyi.fashion.domain.importing.FashionImportBatch;

public record ProductImportView(
        String batchId,
        String batchNo,
        String status,
        String sourceCode,
        String fileName,
        String fileHash,
        Map<String, String> mapping,
        String scopeHash,
        String baseDataHash,
        Instant asOf,
        int actualCount,
        int errorCount,
        long rowVersion,
        List<ImportDetailView> details) {

    public static ProductImportView from(FashionImportBatch batch, List<ImportDetailView> details) {
        return new ProductImportView(
                Long.toString(batch.id()), batch.batchNo(), batch.status(), batch.sourceCode(), batch.fileName(),
                batch.fileHash(), batch.mappingSnapshot(), batch.scopeHash(), batch.baseDataHash(), batch.asOf(),
                batch.actualCount(), batch.errorCount(), batch.rowVersion(), details);
    }
}
