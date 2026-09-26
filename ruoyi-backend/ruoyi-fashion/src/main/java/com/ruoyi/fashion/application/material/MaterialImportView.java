package com.ruoyi.fashion.application.material;

import java.time.Instant;
import java.util.List;

import com.ruoyi.fashion.application.importing.ImportDetailView;
import com.ruoyi.fashion.domain.importing.FashionImportBatch;

public record MaterialImportView(
        String batchId,
        String batchNo,
        String status,
        String sourceCode,
        String fileHash,
        Instant asOf,
        int actualCount,
        int errorCount,
        long rowVersion,
        List<ImportDetailView> details) {

    public static MaterialImportView from(FashionImportBatch batch, List<ImportDetailView> details) {
        return new MaterialImportView(
                Long.toString(batch.id()), batch.batchNo(), batch.status(), batch.sourceCode(), batch.fileHash(),
                batch.asOf(), batch.actualCount(), batch.errorCount(), batch.rowVersion(), details);
    }
}
