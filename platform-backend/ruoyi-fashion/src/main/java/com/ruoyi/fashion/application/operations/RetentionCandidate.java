package com.ruoyi.fashion.application.operations;

import java.time.Instant;

public record RetentionCandidate(
        String category, String recordId, String objectKey, Instant createdAt,
        Instant eligibleAt, String decision, String reason, String quoteId) {
}
