package com.ruoyi.fashion.application.operations;

import java.time.Instant;
import java.util.List;

public record FashionOperationsSnapshot(
        Instant generatedAt,
        Metric importFailureRate,
        Metric imageFailedOrUnknown,
        Metric expiredStock,
        Metric deliveryFailureRate,
        Metric deliveryAverageDurationMs,
        List<AlertCandidate> alertCandidates,
        List<OperationsExceptionItem> exceptions,
        String notificationStatus) {

    public record Metric(String code, String value, String unit, String window) {
    }

    public record AlertCandidate(String code, boolean thresholdReached, String currentValue,
            String thresholdValue, String description) {
    }
}
