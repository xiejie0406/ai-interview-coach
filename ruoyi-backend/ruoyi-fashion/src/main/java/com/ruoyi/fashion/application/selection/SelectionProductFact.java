package com.ruoyi.fashion.application.selection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ruoyi.fashion.domain.product.FashionProductImage;

/** 生成冻结候选所需的当前 SKU 事实；不作为报价历史替代品。 */
public record SelectionProductFact(
        long productId,
        String sourceCode,
        String skuCode,
        String styleCode,
        String productName,
        String categoryCode,
        String colorCode,
        String colorName,
        String sizeCode,
        String sizeSystem,
        String unit,
        BigDecimal salePrice,
        Instant priceAsOf,
        Long priceBatchId,
        String season,
        List<String> tags,
        String mainImageKey,
        List<FashionProductImage> images,
        long visualVersion,
        long productRowVersion,
        int availableQty,
        Instant stockAsOf,
        Long stockBatchId,
        long stockRowVersion) {
}
