package com.ruoyi.interview.domain.practice;

import com.ruoyi.interview.domain.platform.AggregateRoot;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 文本单题练习聚合；AI 反馈属于 evaluation，不进入该聚合的事实。 */
public final class PracticeAttempt extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final ImmutableVersionRef questionVersion;
    private final ImmutableVersionRef rubricVersion;
    private final Instant startedAt;
    private final List<AnswerVersion> answerVersions;
    private PracticeAttemptState state;
    private Optional<DraftAnswer> draft;
    private Instant submittedAt;
    private Instant cancelledAt;
    private AggregateVersion version;

    private PracticeAttempt(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion,
            Instant startedAt,
            PracticeAttemptState state,
            Optional<DraftAnswer> draft,
            List<AnswerVersion> answerVersions,
            Instant submittedAt,
            Instant cancelledAt,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "practiceAttemptId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.questionVersion = DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        this.rubricVersion = DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        this.startedAt = DomainPreconditions.requireNonNull(startedAt, "startedAt");
        this.state = DomainPreconditions.requireNonNull(state, "practiceAttemptState");
        this.draft = draft == null ? Optional.empty() : draft;
        this.answerVersions = new ArrayList<>(answerVersions == null ? List.of() : answerVersions);
        this.submittedAt = submittedAt;
        this.cancelledAt = cancelledAt;
        this.version = DomainPreconditions.requireNonNull(version, "practiceAttemptVersion");
        assertConsistentState();
    }

    public static PracticeAttempt start(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion,
            EventContext context
    ) {
        PracticeAttempt attempt = new PracticeAttempt(id, tenantId, userId, questionVersion, rubricVersion,
                context.occurredAt(), PracticeAttemptState.DRAFT, Optional.empty(), List.of(), null, null,
                AggregateVersion.initial());
        attempt.recordEvent("practice.attempt.started", tenantId, id, attempt.version, context,
                Map.of("questionVersionId", questionVersion.resourceId().value().toString()));
        return attempt;
    }

    /** 从 owner-scoped persistence 重建；不产生事件。 */
    public static PracticeAttempt rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion,
            Instant startedAt,
            PracticeAttemptState state,
            Optional<DraftAnswer> draft,
            List<AnswerVersion> answerVersions,
            Instant submittedAt,
            Instant cancelledAt,
            AggregateVersion version
    ) {
        return new PracticeAttempt(id, tenantId, userId, questionVersion, rubricVersion, startedAt,
                state, draft, answerVersions, submittedAt, cancelledAt, version);
    }

    public ResourceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public ImmutableVersionRef questionVersion() {
        return questionVersion;
    }

    public ImmutableVersionRef rubricVersion() {
        return rubricVersion;
    }

    public PracticeAttemptState state() {
        return state;
    }

    public Optional<DraftAnswer> draft() {
        return draft;
    }

    public List<AnswerVersion> answerVersions() {
        return List.copyOf(answerVersions);
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public AggregateVersion version() {
        return version;
    }

    public void saveDraft(
            String text,
            String contentHash,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == PracticeAttemptState.DRAFT, DomainErrorCode.INVALID_STATE,
                "only a draft practice attempt can be edited");
        draft = Optional.of(new DraftAnswer(text, contentHash, context.occurredAt()));
        bump("practice.draft.saved", context, Map.of());
    }

    public void submit(AnswerVersion answerVersion, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == PracticeAttemptState.DRAFT, DomainErrorCode.ANSWER_ALREADY_SUBMITTED,
                "practice attempt has already been finalized");
        DomainPreconditions.require(answerVersion.tenantId().equals(tenantId)
                        && answerVersion.attemptId().equals(id)
                        && answerVersion.submittedBy().equals(userId),
                DomainErrorCode.OWNERSHIP_DENIED, "answer version belongs to another attempt or owner");
        DomainPreconditions.require(answerVersion.versionNo() == answerVersions.size() + 1,
                DomainErrorCode.VERSION_CONFLICT, "answer version number is not the next immutable version");
        answerVersions.add(answerVersion);
        submittedAt = answerVersion.submittedAt();
        draft = Optional.empty();
        state = PracticeAttemptState.SUBMITTED;
        bump("practice.answer.submitted", context,
                Map.of("answerVersionId", answerVersion.id().value().toString(),
                        "answerVersionNo", Integer.toString(answerVersion.versionNo())));
    }

    public void cancel(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == PracticeAttemptState.DRAFT, DomainErrorCode.INVALID_STATE,
                "only an unsubmitted practice attempt can be cancelled");
        state = PracticeAttemptState.CANCELLED;
        cancelledAt = context.occurredAt();
        draft = Optional.empty();
        bump("practice.attempt.cancelled", context, Map.of());
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private void assertConsistentState() {
        if (state == PracticeAttemptState.SUBMITTED) {
            DomainPreconditions.require(!answerVersions.isEmpty() && submittedAt != null,
                    DomainErrorCode.INVALID_STATE, "submitted attempt requires an immutable answer version");
        }
        if (state == PracticeAttemptState.CANCELLED) {
            DomainPreconditions.require(cancelledAt != null && answerVersions.isEmpty(),
                    DomainErrorCode.INVALID_STATE, "cancelled attempt cannot contain submitted answers");
        }
    }
}
