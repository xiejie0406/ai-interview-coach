package com.ruoyi.fashion.controller.rest.image;

import java.util.List;

import com.ruoyi.fashion.application.image.QuoteImageReviewCommand;

public record QuoteImageReviewRequest(
        int resultNo,
        String decision,
        List<String> checklist,
        String reason,
        long rowVersion) {
    QuoteImageReviewCommand toCommand() {
        return new QuoteImageReviewCommand(resultNo, decision, checklist, reason, rowVersion);
    }
}
