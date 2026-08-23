package com.aiinterviewcoach.application.agent.learning;

import com.aiinterviewcoach.domain.platform.PromptSchemaPin;
import com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Provider-neutral Learning Coach；只返回 learning-plan-v1 候选，不能直接成为权威 Plan。 */
public interface LearningCoachPort {

    Result recommend(Request request);

    record Request(TenantId tenantId, ReportSnapshot report,
                   List<AllowedQuestionVersion> allowedQuestions,
                   String configVersionId, PromptSchemaPin schemaPin,
                   ProviderPolicySnapshot providerPolicy, CorrelationId correlationId) {
        public Request {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(report, "reportSnapshot");
            allowedQuestions = List.copyOf(allowedQuestions == null ? List.of() : allowedQuestions);
            DomainPreconditions.require(new HashSet<>(allowedQuestions.stream()
                            .map(AllowedQuestionVersion::questionVersionId).toList()).size()
                            == allowedQuestions.size(), DomainErrorCode.INVALID_ARGUMENT,
                    "allowed question versions must be unique");
            configVersionId = DomainPreconditions.requireText(configVersionId, "configVersionId");
            DomainPreconditions.requireNonNull(schemaPin, "schemaPin");
            DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }
    }

    record ReportSnapshot(ResourceId reportId, ResourceId reportVersionId, String contentRef) {
        public ReportSnapshot {
            DomainPreconditions.requireNonNull(reportId, "reportId");
            DomainPreconditions.requireNonNull(reportVersionId, "reportVersionId");
            contentRef = DomainPreconditions.requireText(contentRef, "reportContentRef");
        }

        @Override
        public String toString() {
            return "ReportSnapshot[reportId=" + reportId + ", reportVersionId=" + reportVersionId
                    + ", contentRef=<redacted>]";
        }
    }

    record AllowedQuestionVersion(ResourceId questionVersionId, String title, String contentRef) {
        public AllowedQuestionVersion {
            DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
            title = DomainPreconditions.requireText(title, "questionTitle");
            contentRef = DomainPreconditions.requireText(contentRef, "questionContentRef");
        }

        @Override
        public String toString() {
            return "AllowedQuestionVersion[questionVersionId=" + questionVersionId
                    + ", title=<redacted>, contentRef=<redacted>]";
        }
    }

    record Candidate(ResourceId sourceReportVersionId, List<CandidateItem> items, List<String> limitations) {
        public Candidate {
            DomainPreconditions.requireNonNull(sourceReportVersionId, "sourceReportVersionId");
            items = List.copyOf(items == null ? List.of() : items);
            DomainPreconditions.require(items.size() <= 12, DomainErrorCode.INVALID_ARGUMENT,
                    "learning candidate item count exceeds schema limit");
            limitations = List.copyOf(limitations == null ? List.of() : limitations);
            DomainPreconditions.require(limitations.size() <= 16, DomainErrorCode.INVALID_ARGUMENT,
                    "learning limitation count exceeds schema limit");
        }

        @Override
        public String toString() {
            return "Candidate[sourceReportVersionId=" + sourceReportVersionId
                    + ", itemCount=" + items.size() + ", limitations=<redacted>]";
        }
    }

    record CandidateItem(String candidateItemId, ResourceId questionVersionId, String weaknessRef,
                         List<String> reasonCodes, int priority, Optional<Instant> suggestedDueAt) {
        public CandidateItem {
            candidateItemId = DomainPreconditions.requireText(candidateItemId, "candidateItemId");
            DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
            weaknessRef = DomainPreconditions.requireText(weaknessRef, "weaknessRef");
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
            DomainPreconditions.require(priority >= 1 && priority <= 5, DomainErrorCode.INVALID_ARGUMENT,
                    "candidate priority must be between one and five");
            suggestedDueAt = suggestedDueAt == null ? Optional.empty() : suggestedDueAt;
        }

        @Override
        public String toString() {
            return "CandidateItem[candidateItemId=<redacted>, questionVersionId=" + questionVersionId
                    + ", weaknessRef=<redacted>, reasonCodes=<redacted>, priority=" + priority
                    + ", suggestedDueAt=" + suggestedDueAt + "]";
        }
    }

    record Result(Candidate candidate, boolean schemaValidated, String failureCode) {
        public Result {
            if (schemaValidated && candidate == null) {
                throw new IllegalArgumentException("validated learning result requires a candidate");
            }
            if (!schemaValidated && (failureCode == null || failureCode.isBlank())) {
                throw new IllegalArgumentException("invalid learning result requires a failureCode");
            }
        }

        @Override
        public String toString() {
            return "Result[candidate=" + (candidate == null ? "<absent>" : "<present>")
                    + ", schemaValidated=" + schemaValidated + ", failureCode="
                    + (failureCode == null ? "<absent>" : "<redacted>") + "]";
        }
    }
}
