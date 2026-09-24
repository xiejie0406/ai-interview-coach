package com.ruoyi.fashion.application.material;

import java.time.Instant;

public record ImageMapping(
        String filename,
        String skuCode,
        String styleCode,
        String colorCode,
        String usage,
        boolean main,
        boolean colorConfirmed,
        String sourceType,
        String jdId,
        String sourceUrl,
        Instant capturedAt,
        boolean allowAi,
        boolean allowProposal,
        boolean allowEcommerce) {
}
