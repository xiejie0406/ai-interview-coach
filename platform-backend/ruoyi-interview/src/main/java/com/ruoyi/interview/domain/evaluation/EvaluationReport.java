package com.ruoyi.interview.domain.evaluation;

import com.ruoyi.interview.domain.platform.AggregateRoot;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Report root 与一个不可变当前 ReportVersion；反馈不会修改历史版本。 */
public final class EvaluationReport extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final ResourceId evaluationId;
    private final ResourceId sourceInterviewId;
    private final List<FeedbackNote> feedbackNotes;
    private ReportStatus status;
    private ReportVersion reportVersion;
    private String failureCode;
    private AggregateVersion version;

    private EvaluationReport(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId evaluationId,
            ResourceId sourceInterviewId,
            ReportStatus status,
            ReportVersion reportVersion,
            String failureCode,
            List<FeedbackNote> feedbackNotes,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "reportId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.evaluationId = DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
        this.sourceInterviewId = DomainPreconditions.requireNonNull(sourceInterviewId, "sourceInterviewId");
        this.status = DomainPreconditions.requireNonNull(status, "reportStatus");
        this.reportVersion = reportVersion;
        this.failureCode = failureCode;
        this.feedbackNotes = new ArrayList<>(feedbackNotes == null ? List.of() : feedbackNotes);
        this.version = DomainPreconditions.requireNonNull(version, "reportAggregateVersion");
        assertState();
    }

    public static EvaluationReport publish(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId evaluationId,
            ResourceId sourceInterviewId,
            ReportVersion reportVersion,
            boolean partial,
            EventContext context
    ) {
        EvaluationReport report = new EvaluationReport(id, tenantId, userId, evaluationId, sourceInterviewId,
                partial ? ReportStatus.PARTIAL : ReportStatus.READY, reportVersion, null, List.of(),
                AggregateVersion.initial());
        report.recordEvent("evaluation.report.published", tenantId, id, report.version, context,
                Map.of("evaluationId", evaluationId.value(),
                        "reportVersionId", reportVersion.id().value(),
                        "state", report.status.name()));
        return report;
    }

    public static EvaluationReport rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId evaluationId,
            ResourceId sourceInterviewId,
            ReportStatus status,
            ReportVersion reportVersion,
            String failureCode,
            List<FeedbackNote> feedbackNotes,
            AggregateVersion version
    ) {
        return new EvaluationReport(id, tenantId, userId, evaluationId, sourceInterviewId, status,
                reportVersion, failureCode, feedbackNotes, version);
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UserId userId() { return userId; }
    public ResourceId evaluationId() { return evaluationId; }
    public ResourceId sourceInterviewId() { return sourceInterviewId; }
    public ReportStatus status() { return status; }
    public Optional<ReportVersion> reportVersion() { return Optional.ofNullable(reportVersion); }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public List<FeedbackNote> feedbackNotes() { return List.copyOf(feedbackNotes); }
    public AggregateVersion version() { return version; }
    public Optional<Instant> publishedAt() {
        return reportVersion().map(ReportVersion::createdAt);
    }

    public void appendFeedback(FeedbackNote note, AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == ReportStatus.READY || status == ReportStatus.PARTIAL,
                DomainErrorCode.INVALID_STATE, "feedback requires a readable report version");
        DomainPreconditions.requireNonNull(note, "feedbackNote");
        DomainPreconditions.require(feedbackNotes.stream()
                        .noneMatch(existing -> existing.feedbackId().equals(note.feedbackId())),
                DomainErrorCode.IDEMPOTENCY_CONFLICT, "feedback note has already been appended");
        feedbackNotes.add(note);
        version = version.next();
        recordEvent("evaluation.report.feedback_appended", tenantId, id, version, context,
                Map.of("evaluationId", evaluationId.value(), "feedbackId", note.feedbackId().value(),
                        "feedbackType", note.type().name()));
    }

    private void assertState() {
        boolean readable = status == ReportStatus.READY || status == ReportStatus.PARTIAL;
        DomainPreconditions.require(readable == (reportVersion != null), DomainErrorCode.INVALID_STATE,
                "readable report state and report version are inconsistent");
        if (status == ReportStatus.FAILED) {
            DomainPreconditions.require(failureCode != null && !failureCode.isBlank(),
                    DomainErrorCode.INVALID_STATE, "failed report requires a reason code");
        }
    }

    @Override
    public String toString() {
        return "EvaluationReport[id=" + id + ", tenantId=" + tenantId + ", userId=" + userId
                + ", evaluationId=" + evaluationId + ", sourceInterviewId=" + sourceInterviewId
                + ", status=" + status + ", reportVersion="
                + (reportVersion == null ? "<absent>" : reportVersion.id())
                + ", content=<redacted>, feedbackCount=" + feedbackNotes.size() + "]";
    }
}
