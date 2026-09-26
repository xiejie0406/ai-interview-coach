package com.ruoyi.fashion.application.operations;

import java.time.Instant;
import java.util.List;

public record RetentionDryRun(
        Instant generatedAt, boolean deletionEnabled, int quoteDays, int importFileDays,
        int failedImageDays, List<RetentionCandidate> candidates) {
}
