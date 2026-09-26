package com.ruoyi.fashion.domain.stock;

import java.time.Instant;

/** SKU 在单一仓库的当前可售库存；无记录表示未知，不等于零。 */
public record FashionStock(
        long id,
        long productId,
        String warehouseCode,
        int availableQty,
        Instant asOf,
        String confirmationType,
        Long verifiedBy,
        String evidenceNote,
        long lastImportBatchId,
        long createBy,
        Instant createTime,
        long updateBy,
        Instant updateTime,
        long rowVersion) {
}
