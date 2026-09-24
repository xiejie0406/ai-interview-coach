package com.ruoyi.fashion.application.image;

import tools.jackson.databind.JsonNode;

public record QuoteImageCreateCommand(
        String comboId,
        String imageType,
        String sourceMode,
        int requestedCount,
        JsonNode parameters,
        String requestKey,
        long quoteRowVersion,
        long comboRowVersion,
        String comboVisualHash) {
}
