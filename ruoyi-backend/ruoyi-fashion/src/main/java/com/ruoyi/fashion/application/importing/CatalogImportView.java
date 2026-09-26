package com.ruoyi.fashion.application.importing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ruoyi.fashion.domain.importing.FashionImportBatch;

public record CatalogImportView(
        String batchId,
        String batchNo,
        String importType,
        String operationType,
        String sourceBatchId,
        String status,
        String sourceCode,
        String warehouseCode,
        String fileName,
        String fileHash,
        Map<String, Object> scope,
        String scopeHash,
        String baseDataHash,
        Instant asOf,
        int expectedCount,
        int actualCount,
        int errorCount,
        String errorMessage,
        String operatorNote,
        long rowVersion,
        List<ImportDetailView> details) {

    public static CatalogImportView from(FashionImportBatch batch, List<ImportDetailView> details) {
        return new CatalogImportView(
                Long.toString(batch.id()), batch.batchNo(), batch.importType(), batch.operationType(),
                batch.sourceBatchId() == null ? null : Long.toString(batch.sourceBatchId()), batch.status(),
                batch.sourceCode(), batch.warehouseCode(), batch.fileName(), batch.fileHash(), batch.scope(),
                batch.scopeHash(), batch.baseDataHash(), batch.asOf(), batch.expectedCount(), batch.actualCount(),
                batch.errorCount(), batch.errorMessage(), batch.operatorNote(), batch.rowVersion(), details);
    }
}
