package com.ruoyi.fashion.domain.importing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ruoyi.fashion.application.importing.ImportRowError;

public record FashionImportDetail(
        long id,
        long batchId,
        int detailNo,
        Integer sourceRowNo,
        Long productId,
        String businessKey,
        Map<String, Object> rawData,
        Map<String, Object> normalizedData,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String status,
        List<ImportRowError> errors,
        String changeType,
        long createBy,
        Instant createTime,
        long updateBy,
        Instant updateTime,
        long rowVersion,
        String rowType) {
}
