package com.ruoyi.fashion.domain.product;

import java.time.Instant;

/** 商品当前图片的有界 JSON 项；对象本体保存在私有对象存储。 */
public record FashionProductImage(
        String imageId,
        String objectKey,
        String sha256,
        String usage,
        String sourceType,
        String jdId,
        String sourceUrl,
        Instant capturedAt,
        boolean allowInternal,
        boolean allowAi,
        boolean allowProposal,
        boolean allowEcommerce,
        String status,
        long confirmedBy,
        Instant confirmedAt,
        int width,
        int height,
        String originalFilename) {
}
