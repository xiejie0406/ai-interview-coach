package com.aiinterviewcoach.application.learning;

import com.aiinterviewcoach.domain.learning.LearningItemStatus;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;

/** Dashboard read-model owner；stale/failed 必须显式，不能用空数组伪装 fresh。 */
public interface LearningDashboardPort {
    Projection load(TenantId tenantId, UserId userId);

    enum RefreshState { FRESH, REFRESHING, STALE, FAILED }
    enum TrendDirection { IMPROVING, STABLE, DECLINING, UNKNOWN }
    enum ReportState { PENDING, RUNNING, READY, PARTIAL, FAILED, CANCELLED }

    record Projection(long projectionVersion, Instant generatedAt, RefreshState refreshState,
                      List<TodayItem> todayItems, List<RecentReport> recentReports,
                      List<Trend> trends) {
        public Projection {
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(projectionVersion >= 0,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "dashboard projection version must not be negative");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(
                    generatedAt, "dashboardGeneratedAt");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(
                    refreshState, "dashboardRefreshState");
            todayItems = List.copyOf(todayItems == null ? List.of() : todayItems);
            recentReports = List.copyOf(recentReports == null ? List.of() : recentReports);
            trends = List.copyOf(trends == null ? List.of() : trends);
        }

        @Override
        public String toString() {
            return "Projection[projectionVersion=" + projectionVersion + ", generatedAt=" + generatedAt
                    + ", refreshState=" + refreshState + ", todayItemCount=" + todayItems.size()
                    + ", recentReportCount=" + recentReports.size() + ", trendCount=" + trends.size() + "]";
        }
    }

    record TodayItem(ResourceId itemId, ResourceId planId, ResourceId questionVersionId,
                     String questionTitle, LearningItemStatus state, Optional<Instant> scheduledAt,
                     List<String> reasonCodes, AggregateVersion version) {
        public TodayItem {
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(itemId, "learningItemId");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(planId, "learningPlanId");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(
                    questionVersionId, "questionVersionId");
            questionTitle = com.aiinterviewcoach.domain.platform.DomainPreconditions.requireText(
                    questionTitle, "questionTitle");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(questionTitle.length() <= 300,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "questionTitle is too long");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(
                    state == LearningItemStatus.PENDING || state == LearningItemStatus.IN_PROGRESS,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "dashboard item must be pending or in progress");
            scheduledAt = scheduledAt == null ? Optional.empty() : scheduledAt;
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(
                    new HashSet<>(reasonCodes).size() == reasonCodes.size(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "dashboard reason codes must be unique");
            reasonCodes.forEach(value -> {
                com.aiinterviewcoach.domain.platform.DomainPreconditions.requireText(value, "reasonCode");
                com.aiinterviewcoach.domain.platform.DomainPreconditions.require(value.length() <= 96,
                        com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                        "reasonCode is too long");
            });
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(version, "learningItemVersion");
        }

        @Override
        public String toString() {
            return "TodayItem[itemId=" + itemId + ", planId=" + planId
                    + ", questionVersionId=" + questionVersionId + ", questionTitle=<redacted>"
                    + ", state=" + state + ", scheduledAt=" + scheduledAt
                    + ", reasonCodes=<redacted>, version=" + version + "]";
        }
    }

    record RecentReport(ResourceId reportId, Optional<ResourceId> reportVersionId, ReportState state,
                        Optional<Instant> completedAt, int limitationCount) {
        public RecentReport {
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(reportId, "reportId");
            reportVersionId = reportVersionId == null ? Optional.empty() : reportVersionId;
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(state, "reportState");
            completedAt = completedAt == null ? Optional.empty() : completedAt;
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(limitationCount >= 0,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "report limitation count must not be negative");
        }
    }

    record Trend(String dimensionId, boolean comparable, String reasonCode,
                 TrendDirection direction, int sampleCount) {
        public Trend {
            dimensionId = com.aiinterviewcoach.domain.platform.DomainPreconditions.requireText(
                    dimensionId, "dimensionId");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(dimensionId.length() <= 128,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "dimensionId is too long");
            reasonCode = com.aiinterviewcoach.domain.platform.DomainPreconditions.requireText(
                    reasonCode, "reasonCode");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(reasonCode.length() <= 96,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "trend reasonCode is too long");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.requireNonNull(direction, "trendDirection");
            com.aiinterviewcoach.domain.platform.DomainPreconditions.require(sampleCount >= 0,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "trend sampleCount must not be negative");
        }

        @Override
        public String toString() {
            return "Trend[dimensionId=" + dimensionId + ", comparable=" + comparable
                    + ", reasonCode=<redacted>, direction=" + direction
                    + ", sampleCount=" + sampleCount + "]";
        }
    }
}
