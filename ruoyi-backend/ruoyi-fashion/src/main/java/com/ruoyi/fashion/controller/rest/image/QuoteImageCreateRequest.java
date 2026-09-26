package com.ruoyi.fashion.controller.rest.image;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.image.QuoteImageCreateCommand;

public record QuoteImageCreateRequest(
        String comboId,
        String imageType,
        String sourceMode,
        int requestedCount,
        JsonNode parameters,
        String requestKey,
        long quoteRowVersion,
        long comboRowVersion,
        String comboVisualHash) {
    QuoteImageCreateCommand toCommand() {
        return new QuoteImageCreateCommand(comboId, imageType, sourceMode, requestedCount, parameters,
                requestKey, quoteRowVersion, comboRowVersion, comboVisualHash);
    }
}
