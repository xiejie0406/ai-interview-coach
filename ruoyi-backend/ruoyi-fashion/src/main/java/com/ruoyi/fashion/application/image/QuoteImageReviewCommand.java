package com.ruoyi.fashion.application.image;

import java.util.List;

public record QuoteImageReviewCommand(
        int resultNo,
        String decision,
        List<String> checklist,
        String reason,
        long rowVersion) {
}
