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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

/** Evaluation orchestration aggregate；只保存稳定引用、结构化产物和固定版本，不保存回答原文。 */
public final class EvaluationRun extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final EvaluationSourceRef sourceRef;
    private final EvaluationPolicySnapshot policySnapshot;
    private EvaluationStatus status;
    private EvaluationStage stage;
    private EvidenceBundle evidenceBundle;
    private RubricScore rubricJudgement;
    private ResourceId evaluationVersionId;
    private ResourceId reportId;
    private String failureCode;
    private final Instant requestedAt;
    private Instant completedAt;
    private AggregateVersion version;

    private EvaluationRun(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            EvaluationSourceRef sourceRef,
            EvaluationPolicySnapshot policySnapshot,
            EvaluationStatus status,
            EvaluationStage stage,
            EvidenceBundle evidenceBundle,
            RubricScore rubricJudgement,
            ResourceId evaluationVersionId,
            ResourceId reportId,
            String failureCode,
            Instant requestedAt,
            Instant completedAt,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "evaluationId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.sourceRef = DomainPreconditions.requireNonNull(sourceRef, "sourceRef");
        this.policySnapshot = DomainPreconditions.requireNonNull(policySnapshot, "policySnapshot");
        this.status = DomainPreconditions.requireNonNull(status, "evaluationStatus");
        this.stage = DomainPreconditions.requireNonNull(stage, "evaluationStage");
        this.evidenceBundle = evidenceBundle;
        this.rubricJudgement = rubricJudgement;
        this.evaluationVersionId = evaluationVersionId;
        this.reportId = reportId;
        this.failureCode = failureCode;
        this.requestedAt = DomainPreconditions.requireNonNull(requestedAt, "requestedAt");
        this.completedAt = completedAt;
        this.version = DomainPreconditions.requireNonNull(version, "evaluationAggregateVersion");
        assertState();
    }

    public static EvaluationRun request(ResourceId id, TenantId tenantId, UserId userId,
                                        EvaluationSourceRef sourceRef,
                                        EvaluationPolicySnapshot policySnapshot,
                                        EventContext context) {
        EvaluationRun run = new EvaluationRun(id, tenantId, userId, sourceRef, policySnapshot,
                EvaluationStatus.PENDING, EvaluationStage.QUEUED, null, null, null, null,
                null, context.occurredAt(), null, AggregateVersion.initial());
        run.recordEvent("evaluation.requested", tenantId, id, run.version, context,
                Map.of("answerVersionId", sourceRef.answerVersionId().value(),
                        "sourceInterviewId", sourceRef.interviewId().value(),
                        "configVersionId", policySnapshot.configVersionId()));
        return run;
    }

    public static EvaluationRun rehydrate(ResourceId id, TenantId tenantId, UserId userId,
                                          EvaluationSourceRef sourceRef,
                                          EvaluationPolicySnapshot policySnapshot,
                                          EvaluationStatus status, EvaluationStage stage,
                                          EvidenceBundle evidenceBundle, RubricScore rubricJudgement,
                                          ResourceId evaluationVersionId, ResourceId reportId,
                                          String failureCode, Instant requestedAt, Instant completedAt,
                                          AggregateVersion version) {
        return new EvaluationRun(id, tenantId, userId, sourceRef, policySnapshot, status, stage,
                evidenceBundle, rubricJudgement, evaluationVersionId, reportId, failureCode,
                requestedAt, completedAt, version);
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UserId userId() { return userId; }
    public EvaluationSourceRef sourceRef() { return sourceRef; }
    public EvaluationPolicySnapshot policySnapshot() { return policySnapshot; }
    public EvaluationStatus status() { return status; }
    public EvaluationStage stage() { return stage; }
    public Optional<EvidenceBundle> evidenceBundle() { return Optional.ofNullable(evidenceBundle); }
    public Optional<RubricScore> rubricJudgement() { return Optional.ofNullable(rubricJudgement); }
    public Optional<ResourceId> evaluationVersionId() { return Optional.ofNullable(evaluationVersionId); }
    public Optional<ResourceId> reportId() { return Optional.ofNullable(reportId); }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public Instant requestedAt() { return requestedAt; }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public AggregateVersion version() { return version; }

    public void start(AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == EvaluationStatus.PENDING, DomainErrorCode.INVALID_STATE,
                "only pending evaluation can start");
        status = EvaluationStatus.RUNNING;
        stage = EvaluationStage.EVIDENCE_EXTRACTING;
        bump("evaluation.evidence.extracting", context, Map.of());
    }

    public void attachEvidence(EvidenceBundle bundle, AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == EvaluationStatus.RUNNING
                        && stage == EvaluationStage.EVIDENCE_EXTRACTING,
                DomainErrorCode.INVALID_STATE, "evaluation is not extracting evidence");
        DomainPreconditions.requireNonNull(bundle, "evidenceBundle");
        DomainPreconditions.require(bundle.schemaPin().equals(policySnapshot.evidencePin())
                        && bundle.answerVersionId().equals(sourceRef.answerVersionId())
                        && bundle.answerHash().equals(sourceRef.answerHash()),
                DomainErrorCode.INVALID_ARGUMENT, "evidence bundle does not match pinned input/schema");
        evidenceBundle = bundle;
        stage = EvaluationStage.RUBRIC_JUDGING;
        bump("evaluation.evidence.attached", context, Map.of("evidenceBundleId", bundle.bundleId().value()));
    }

    public void attachRubricJudgement(RubricScore judgement, ResourceId immutableEvaluationVersionId,
                                      AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == EvaluationStatus.RUNNING
                        && stage == EvaluationStage.RUBRIC_JUDGING,
                DomainErrorCode.INVALID_STATE, "evaluation is not judging rubric");
        DomainPreconditions.requireNonNull(judgement, "rubricJudgement");
        DomainPreconditions.require(judgement.schemaPin().equals(policySnapshot.judgePin())
                        && judgement.rubricVersionId().equals(policySnapshot.rubricVersionId()),
                DomainErrorCode.INVALID_ARGUMENT, "rubric judgement does not match pinned schema/rubric");
        var available = new HashSet<>(evidenceBundle.spans().stream().map(EvidenceItem::evidenceId).toList());
        DomainPreconditions.require(available.containsAll(judgement.referencedEvidenceIds()),
                DomainErrorCode.INVALID_ARGUMENT, "rubric judgement references unknown evidence");
        rubricJudgement = judgement;
        evaluationVersionId = DomainPreconditions.requireNonNull(immutableEvaluationVersionId,
                "evaluationVersionId");
        stage = EvaluationStage.REPORT_COMPOSING;
        bump("evaluation.rubric.attached", context,
                Map.of("judgementId", judgement.judgementId().value(),
                        "evaluationVersionId", evaluationVersionId.value()));
    }

    public void complete(ResourceId publishedReportId, AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == EvaluationStatus.RUNNING
                        && stage == EvaluationStage.REPORT_COMPOSING,
                DomainErrorCode.INVALID_STATE, "evaluation is not composing a report");
        reportId = DomainPreconditions.requireNonNull(publishedReportId, "publishedReportId");
        status = EvaluationStatus.SUCCEEDED;
        stage = EvaluationStage.COMPLETE;
        completedAt = context.occurredAt();
        failureCode = null;
        bump("evaluation.succeeded", context, Map.of("reportId", reportId.value()));
    }

    public void requireManualReview(String reasonCode, AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        requireNotFinal();
        status = EvaluationStatus.RUNNING;
        stage = EvaluationStage.MANUAL_REVIEW;
        failureCode = DomainPreconditions.requireText(reasonCode, "manualReviewReasonCode");
        bump("evaluation.manual_review_required", context, Map.of("reasonCode", failureCode));
    }

    public void fail(String reasonCode, boolean retryable, AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        requireNotFinal();
        failureCode = DomainPreconditions.requireText(reasonCode, "failureCode");
        status = retryable ? EvaluationStatus.FAILED_RETRYABLE : EvaluationStatus.FAILED_FINAL;
        completedAt = retryable ? null : context.occurredAt();
        bump(retryable ? "evaluation.failed_retryable" : "evaluation.failed_final", context,
                Map.of("failureCode", failureCode));
    }

    public void retry(AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == EvaluationStatus.FAILED_RETRYABLE,
                DomainErrorCode.RETRY_NOT_ALLOWED, "evaluation is not retryable");
        status = EvaluationStatus.RUNNING;
        failureCode = null;
        bump("evaluation.retry_started", context, Map.of("stage", stage.name()));
    }

    public void cancel(AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        requireNotFinal();
        status = EvaluationStatus.CANCELLED;
        completedAt = context.occurredAt();
        bump("evaluation.cancelled", context, Map.of("stage", stage.name()));
    }

    private void requireNotFinal() {
        DomainPreconditions.require(status != EvaluationStatus.SUCCEEDED
                        && status != EvaluationStatus.FAILED_FINAL
                        && status != EvaluationStatus.CANCELLED,
                DomainErrorCode.INVALID_STATE, "final evaluation cannot transition");
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
        assertState();
    }

    private void assertState() {
        if (stage == EvaluationStage.RUBRIC_JUDGING || stage == EvaluationStage.REPORT_COMPOSING
                || stage == EvaluationStage.COMPLETE) {
            DomainPreconditions.require(evidenceBundle != null, DomainErrorCode.INVALID_STATE,
                    "evaluation stage requires evidence");
        }
        if (stage == EvaluationStage.REPORT_COMPOSING || stage == EvaluationStage.COMPLETE) {
            DomainPreconditions.require(rubricJudgement != null && evaluationVersionId != null,
                    DomainErrorCode.INVALID_STATE, "evaluation stage requires judgement and immutable version");
        }
        if (status == EvaluationStatus.SUCCEEDED) {
            DomainPreconditions.require(stage == EvaluationStage.COMPLETE && reportId != null && completedAt != null,
                    DomainErrorCode.INVALID_STATE, "succeeded evaluation requires complete report state");
        }
        if (status == EvaluationStatus.FAILED_RETRYABLE || status == EvaluationStatus.FAILED_FINAL
                || stage == EvaluationStage.MANUAL_REVIEW) {
            DomainPreconditions.require(failureCode != null && !failureCode.isBlank(),
                    DomainErrorCode.INVALID_STATE, "failure/review state requires a reason code");
        }
    }

    @Override
    public String toString() {
        return "EvaluationRun[id=" + id + ", tenantId=" + tenantId + ", userId=" + userId
                + ", state=" + status + ", stage=" + stage + ", version=" + version + "]";
    }
}
