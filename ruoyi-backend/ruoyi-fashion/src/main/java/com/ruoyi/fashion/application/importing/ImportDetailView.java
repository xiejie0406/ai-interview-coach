package com.ruoyi.fashion.application.importing;

import java.util.List;
import java.util.Map;

import com.ruoyi.fashion.domain.importing.FashionImportDetail;

public record ImportDetailView(
        String id,
        int detailNo,
        Integer sourceRowNo,
        String productId,
        String businessKey,
        Map<String, Object> rawData,
        Map<String, Object> normalizedData,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String status,
        List<ImportRowError> errors,
        String changeType,
        String rowType) {

    public static ImportDetailView from(FashionImportDetail detail) {
        return new ImportDetailView(
                Long.toString(detail.id()), detail.detailNo(), detail.sourceRowNo(),
                detail.productId() == null ? null : Long.toString(detail.productId()),
                detail.businessKey(), detail.rawData(), detail.normalizedData(), detail.beforeData(),
                detail.afterData(), detail.status(), detail.errors(), detail.changeType(), detail.rowType());
    }
}
