package com.ruoyi.interview.domain.catalog;

import com.ruoyi.interview.domain.platform.AggregateRoot;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.util.Map;
import java.util.Optional;

/** Question 根负责版本指针和发布生命周期，不允许其他域静默覆盖已发布版本。 */
public final class Question extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final String stableKey;
    private QuestionStatus status;
    private Optional<ImmutableVersionRef> currentDraftVersion;
    private Optional<ImmutableVersionRef> currentPublishedVersion;
    private AggregateVersion version;

    private Question(
            ResourceId id,
            TenantId tenantId,
            String stableKey,
            QuestionStatus status,
            Optional<ImmutableVersionRef> currentDraftVersion,
            Optional<ImmutableVersionRef> currentPublishedVersion,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "questionId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.stableKey = DomainPreconditions.requireText(stableKey, "stableKey");
        this.status = DomainPreconditions.requireNonNull(status, "questionStatus");
        this.currentDraftVersion = currentDraftVersion == null ? Optional.empty() : currentDraftVersion;
        this.currentPublishedVersion = currentPublishedVersion == null ? Optional.empty() : currentPublishedVersion;
        this.version = DomainPreconditions.requireNonNull(version, "questionAggregateVersion");
        if (status == QuestionStatus.IN_REVIEW || status == QuestionStatus.PUBLISHED_WITH_REVIEW) {
            DomainPreconditions.require(this.currentDraftVersion.isPresent(), DomainErrorCode.INVALID_STATE,
                    "in-review question requires a draft version");
        }
        if (status == QuestionStatus.PUBLISHED
                || status == QuestionStatus.PUBLISHED_WITH_DRAFT
                || status == QuestionStatus.PUBLISHED_WITH_REVIEW
                || status == QuestionStatus.RETIRED) {
            DomainPreconditions.require(this.currentPublishedVersion.isPresent(), DomainErrorCode.INVALID_STATE,
                    "published or retired question requires a published version");
        }
        if (status == QuestionStatus.PUBLISHED_WITH_DRAFT || status == QuestionStatus.PUBLISHED_WITH_REVIEW) {
            DomainPreconditions.require(this.currentDraftVersion.isPresent(), DomainErrorCode.INVALID_STATE,
                    "published question workflow state requires a draft version");
        }
        if (status == QuestionStatus.PUBLISHED) {
            DomainPreconditions.require(this.currentDraftVersion.isEmpty(), DomainErrorCode.INVALID_STATE,
                    "published question without workflow cannot retain a draft pointer");
        }
        DomainPreconditions.require(!((status == QuestionStatus.DRAFT || status == QuestionStatus.IN_REVIEW)
                        && this.currentPublishedVersion.isPresent()),
                DomainErrorCode.INVALID_STATE, "unpublished workflow state cannot hide a published version");
        if (status == QuestionStatus.RETIRED) {
            DomainPreconditions.require(this.currentDraftVersion.isEmpty(), DomainErrorCode.INVALID_STATE,
                    "retired question cannot retain a draft workflow");
        }
    }

    public static Question draft(ResourceId id, TenantId tenantId, String stableKey, EventContext context) {
        Question question = new Question(id, tenantId, stableKey, QuestionStatus.DRAFT,
                Optional.empty(), Optional.empty(), AggregateVersion.initial());
        question.recordEvent("catalog.question.created", tenantId, id, question.version, context,
                Map.of("stableKey", question.stableKey));
        return question;
    }

    public static Question rehydrate(
            ResourceId id,
            TenantId tenantId,
            String stableKey,
            QuestionStatus status,
            Optional<ImmutableVersionRef> currentDraftVersion,
            Optional<ImmutableVersionRef> currentPublishedVersion,
            AggregateVersion version
    ) {
        return new Question(id, tenantId, stableKey, status, currentDraftVersion,
                currentPublishedVersion, version);
    }

    public ResourceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String stableKey() {
        return stableKey;
    }

    public QuestionStatus status() {
        return status;
    }

    public Optional<ImmutableVersionRef> currentDraftVersion() {
        return currentDraftVersion;
    }

    public Optional<ImmutableVersionRef> currentPublishedVersion() {
        return currentPublishedVersion;
    }

    public AggregateVersion version() {
        return version;
    }

    public void attachDraft(QuestionVersion draftVersion, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(draftVersion.tenantId().equals(tenantId)
                        && draftVersion.questionId().equals(id),
                DomainErrorCode.TENANT_MISMATCH, "question draft belongs to another aggregate");
        DomainPreconditions.require(status == QuestionStatus.DRAFT
                        || status == QuestionStatus.PUBLISHED
                        || status == QuestionStatus.PUBLISHED_WITH_DRAFT,
                DomainErrorCode.INVALID_STATE, "question cannot receive a draft while under review or retired");
        int latestVersionNo = java.util.stream.Stream.concat(
                        currentDraftVersion.stream(), currentPublishedVersion.stream())
                .mapToInt(ImmutableVersionRef::versionNo)
                .max()
                .orElse(0);
        DomainPreconditions.require(draftVersion.versionNo() == latestVersionNo + 1,
                DomainErrorCode.VERSION_CONFLICT, "question draft must be the next immutable version");
        currentDraftVersion = Optional.of(draftVersion.versionRef());
        status = currentPublishedVersion.isPresent()
                ? QuestionStatus.PUBLISHED_WITH_DRAFT : QuestionStatus.DRAFT;
        bump("catalog.question.draft_attached", context, Map.of("versionNo", Integer.toString(draftVersion.versionNo())));
    }

    public void submitForReview(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(currentDraftVersion.isPresent(), DomainErrorCode.QUESTION_NOT_PUBLISHABLE,
                "question requires a draft version");
        DomainPreconditions.require(status == QuestionStatus.DRAFT
                        || status == QuestionStatus.PUBLISHED_WITH_DRAFT,
                DomainErrorCode.INVALID_STATE,
                "only a draft question can be submitted for review");
        status = currentPublishedVersion.isPresent()
                ? QuestionStatus.PUBLISHED_WITH_REVIEW : QuestionStatus.IN_REVIEW;
        bump("catalog.question.submitted_for_review", context, Map.of());
    }

    /**
     * 记录 Rubric 版本与题目聚合的绑定事实，并推进聚合版本。
     *
     * Rubric 正文本身是独立的不可变事实，但它参与后续发布门禁；因此绑定动作必须
     * 经过 Question 的乐观锁并写入同一 Outbox，避免两个编辑器使用同一 ETag 同时成功。
     */
    public void attachRubric(RubricVersion rubricVersion,
                             AggregateVersion expectedVersion,
                             EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        DomainPreconditions.requireNonNull(context, "eventContext");
        DomainPreconditions.require(rubricVersion.tenantId().equals(tenantId),
                DomainErrorCode.TENANT_MISMATCH, "rubric belongs to another tenant");
        DomainPreconditions.require(status != QuestionStatus.RETIRED,
                DomainErrorCode.INVALID_STATE, "retired question cannot receive a rubric");
        bump("catalog.question.rubric_attached", context,
                Map.of("questionVersionId", rubricVersion.questionVersionId().value().toString(),
                        "rubricVersionId", rubricVersion.id().value().toString(),
                        "rubricVersionNo", Integer.toString(rubricVersion.versionNo())));
    }

    public QuestionPublication publish(
            ResourceId publicationId,
            QuestionVersion questionVersion,
            RubricVersion rubricVersion,
            UserId reviewedBy,
            String reasonCode,
            PublicationPolicy policy,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.requireNonNull(publicationId, "publicationId");
        DomainPreconditions.requireNonNull(reviewedBy, "reviewedBy");
        DomainPreconditions.requireNonNull(policy, "publicationPolicy");
        DomainPreconditions.requireNonNull(context, "eventContext");
        reasonCode = DomainPreconditions.requireText(reasonCode, "publicationReasonCode");
        DomainPreconditions.require(reasonCode.matches("[A-Z][A-Z0-9_]{0,63}"),
                DomainErrorCode.INVALID_ARGUMENT, "publication reason code is invalid");
        policy.assertPublishable(this, questionVersion, rubricVersion);
        DomainPreconditions.require(currentDraftVersion.filter(ref -> ref.equals(questionVersion.versionRef())).isPresent(),
                DomainErrorCode.VERSION_CONFLICT, "published version must be the current draft");
        currentPublishedVersion = Optional.of(questionVersion.versionRef());
        currentDraftVersion = Optional.empty();
        status = QuestionStatus.PUBLISHED;
        bump("catalog.question.published", context,
                Map.of("questionVersionId", questionVersion.id().value().toString(),
                        "rubricVersionId", rubricVersion.id().value().toString(),
                        "publicationId", publicationId.value().toString(),
                        "reasonCode", reasonCode,
                        "reviewedBy", reviewedBy.value().toString()));
        ContentSourceReference source = questionVersion.source().orElseThrow();
        return new QuestionPublication(
                publicationId,
                tenantId,
                id,
                questionVersion.versionRef(),
                rubricVersion.versionRef(),
                source.verificationFactId(),
                reviewedBy,
                reasonCode,
                context.occurredAt());
    }

    public void rejectReview(
            String reasonCode,
            UserId reviewedBy,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == QuestionStatus.IN_REVIEW
                        || status == QuestionStatus.PUBLISHED_WITH_REVIEW,
                DomainErrorCode.INVALID_STATE,
                "only an in-review question can be rejected");
        DomainPreconditions.requireNonNull(reviewedBy, "reviewedBy");
        reasonCode = DomainPreconditions.requireText(reasonCode, "rejectionReasonCode");
        status = currentPublishedVersion.isPresent()
                ? QuestionStatus.PUBLISHED_WITH_DRAFT : QuestionStatus.DRAFT;
        bump("catalog.question.review_rejected", context,
                Map.of("reasonCode", reasonCode, "reviewedBy", reviewedBy.value().toString()));
    }

    public void retire(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == QuestionStatus.PUBLISHED, DomainErrorCode.INVALID_STATE,
                "only a published question can be retired");
        status = QuestionStatus.RETIRED;
        bump("catalog.question.retired", context, Map.of());
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }
}
