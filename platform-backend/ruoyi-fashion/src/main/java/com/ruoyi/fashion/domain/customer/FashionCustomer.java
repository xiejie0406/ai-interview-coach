package com.ruoyi.fashion.domain.customer;

import java.time.Instant;
import java.util.List;

/** 客户业务事实；联系人和内部备注不得进入 AI 上下文。 */
public record FashionCustomer(
        long id,
        String code,
        String name,
        String customerType,
        String contactName,
        String contactPhone,
        String region,
        long salespersonId,
        List<Long> collaboratorIds,
        String internalNote,
        String status,
        long createBy,
        Instant createTime,
        long updateBy,
        Instant updateTime,
        long rowVersion) {
}
