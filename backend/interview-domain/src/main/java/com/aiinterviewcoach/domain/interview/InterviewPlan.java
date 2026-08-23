package com.aiinterviewcoach.domain.interview;

import com.aiinterviewcoach.domain.platform.AggregateRoot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.TimeBudget;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 面试计划草稿和确认门。问题选择、预算、权益预检均由确定性应用流程完成后再传入。
 */
public final class InterviewPlan extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final ImmutableVersionRef profileVersion;
    private final InterviewMode mode;
    private final Instant expiresAt;
    private List<PlannedQuestion> questions;
    private TimeBudget totalTimeBudget;
    private int totalFollowUpBudget;
    private UsageEstimate usageEstimate;
    private String contentHash;
    private int planVersionNo;
    private InterviewPlanState state;
    private Optional<ResourceId> usageReservationId;
    private AggregateVersion version;

    private InterviewPlan(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef profileVersion,
            InterviewMode mode,
            Instant expiresAt,
            List<PlannedQuestion> questions,
            TimeBudget totalTimeBudget,
            int totalFollowUpBudget,
            UsageEstimate usageEstimate,
            String contentHash,
            int planVersionNo,
            InterviewPlanState state,
            Optional<ResourceId> usageReservationId,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "interviewPlanId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.profileVersion = DomainPreconditions.requireNonNull(profileVersion, "profileVersion");
        this.mode = DomainPreconditions.requireNonNull(mode, "interviewMode");
        this.expiresAt = DomainPreconditions.requireNonNull(expiresAt, "planExpiresAt");
        this.questions = validateQuestions(questions);
        this.totalTimeBudget = DomainPreconditions.requireNonNull(totalTimeBudget, "totalTimeBudget");
        DomainPreconditions.require(totalFollowUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "total follow-up budget must not be negative");
        this.totalFollowUpBudget = totalFollowUpBudget;
        PlanPolicy.validate(this.questions, this.totalTimeBudget, this.totalFollowUpBudget);
        this.usageEstimate = DomainPreconditions.requireNonNull(usageEstimate, "usageEstimate");
        this.contentHash = DomainPreconditions.requireText(contentHash, "planContentHash");
        DomainPreconditions.require(planVersionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "plan version number must be positive");
        this.planVersionNo = planVersionNo;
        this.state = DomainPreconditions.requireNonNull(state, "planState");
        this.usageReservationId = usageReservationId == null ? Optional.empty() : usageReservationId;
        this.version = DomainPreconditions.requireNonNull(version, "planAggregateVersion");
        if (state == InterviewPlanState.CONFIRMED) {
            DomainPreconditions.require(this.usageReservationId.isPresent(), DomainErrorCode.INVALID_STATE,
                    "confirmed plan requires a usage reservation");
        }
        if (state == InterviewPlanState.DRAFT || state == InterviewPlanState.EXPIRED) {
            DomainPreconditions.require(this.usageReservationId.isEmpty(), DomainErrorCode.INVALID_STATE,
                    "unconfirmed plan cannot retain a usage reservation");
        }
    }

    public static InterviewPlan draft(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef profileVersion,
            InterviewMode mode,
            Instant expiresAt,
            List<PlannedQuestion> questions,
            TimeBudget totalTimeBudget,
            int totalFollowUpBudget,
            UsageEstimate usageEstimate,
            String contentHash,
            EventContext context
    ) {
        DomainPreconditions.require(expiresAt.isAfter(context.occurredAt()), DomainErrorCode.PLAN_EXPIRED,
                "plan must expire in the future");
        InterviewPlan plan = new InterviewPlan(id, tenantId, userId, profileVersion, mode, expiresAt,
                questions, totalTimeBudget, totalFollowUpBudget, usageEstimate, contentHash, 1,
                InterviewPlanState.DRAFT,
                Optional.empty(), AggregateVersion.initial());
        plan.recordEvent("interview.plan.created", tenantId, id, plan.version, context,
                Map.of("mode", mode.name(), "planVersionNo", "1"));
        return plan;
    }

    /** 从 owner-scoped persistence 重建；不产生事件。 */
    public static InterviewPlan rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ImmutableVersionRef profileVersion,
            InterviewMode mode,
            Instant expiresAt,
            List<PlannedQuestion> questions,
            TimeBudget totalTimeBudget,
            int totalFollowUpBudget,
            UsageEstimate usageEstimate,
            String contentHash,
            int planVersionNo,
            InterviewPlanState state,
            Optional<ResourceId> usageReservationId,
            AggregateVersion version
    ) {
        return new InterviewPlan(id, tenantId, userId, profileVersion, mode, expiresAt, questions,
                totalTimeBudget, totalFollowUpBudget, usageEstimate, contentHash, planVersionNo,
                state, usageReservationId, version);
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

    public ImmutableVersionRef profileVersion() {
        return profileVersion;
    }

    public InterviewMode mode() {
        return mode;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public List<PlannedQuestion> questions() {
        return List.copyOf(questions);
    }

    public TimeBudget totalTimeBudget() {
        return totalTimeBudget;
    }

    public int totalFollowUpBudget() {
        return totalFollowUpBudget;
    }

    public UsageEstimate usageEstimate() {
        return usageEstimate;
    }

    public String contentHash() {
        return contentHash;
    }

    public int planVersionNo() {
        return planVersionNo;
    }

    public InterviewPlanState state() {
        return state;
    }

    public Optional<ResourceId> usageReservationId() {
        return usageReservationId;
    }

    public AggregateVersion version() {
        return version;
    }

    public void revise(
            List<PlannedQuestion> questions,
            TimeBudget totalTimeBudget,
            int totalFollowUpBudget,
            UsageEstimate usageEstimate,
            String contentHash,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        requireDraftAndUnexpired(context.occurredAt());
        this.questions = validateQuestions(questions);
        this.totalTimeBudget = DomainPreconditions.requireNonNull(totalTimeBudget, "totalTimeBudget");
        DomainPreconditions.require(totalFollowUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "total follow-up budget must not be negative");
        this.totalFollowUpBudget = totalFollowUpBudget;
        PlanPolicy.validate(this.questions, this.totalTimeBudget, this.totalFollowUpBudget);
        this.usageEstimate = DomainPreconditions.requireNonNull(usageEstimate, "usageEstimate");
        this.contentHash = DomainPreconditions.requireText(contentHash, "planContentHash");
        planVersionNo++;
        bump("interview.plan.revised", context, Map.of("planVersionNo", Integer.toString(planVersionNo)));
    }

    public InterviewPlanReference confirm(
            ResourceId usageReservationId,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        requireDraftAndUnexpired(context.occurredAt());
        this.usageReservationId = Optional.of(
                DomainPreconditions.requireNonNull(usageReservationId, "usageReservationId"));
        state = InterviewPlanState.CONFIRMED;
        bump("interview.plan.confirmed", context,
                Map.of("planVersionNo", Integer.toString(planVersionNo),
                        "usageReservationId", usageReservationId.value().toString()));
        return confirmedReference();
    }

    public InterviewPlanReference confirmedReference() {
        DomainPreconditions.require(state == InterviewPlanState.CONFIRMED, DomainErrorCode.PLAN_NOT_CONFIRMED,
                "interview plan is not confirmed");
        return new InterviewPlanReference(id, planVersionNo, contentHash, usageReservationId.orElseThrow(),
                questions, totalFollowUpBudget);
    }

    public void cancel(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == InterviewPlanState.DRAFT || state == InterviewPlanState.CONFIRMED,
                DomainErrorCode.INVALID_STATE, "plan cannot be cancelled from current state");
        state = InterviewPlanState.CANCELLED;
        bump("interview.plan.cancelled", context, Map.of());
    }

    public void expire(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == InterviewPlanState.DRAFT, DomainErrorCode.INVALID_STATE,
                "only a draft plan can expire");
        DomainPreconditions.require(!context.occurredAt().isBefore(expiresAt), DomainErrorCode.INVALID_STATE,
                "plan cannot expire before expiresAt");
        state = InterviewPlanState.EXPIRED;
        bump("interview.plan.expired", context, Map.of());
    }

    private void requireDraftAndUnexpired(Instant at) {
        DomainPreconditions.require(state == InterviewPlanState.DRAFT, DomainErrorCode.INVALID_STATE,
                "only a draft plan can be changed or confirmed");
        DomainPreconditions.require(at.isBefore(expiresAt), DomainErrorCode.PLAN_EXPIRED,
                "interview plan has expired");
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private static List<PlannedQuestion> validateQuestions(List<PlannedQuestion> questions) {
        DomainPreconditions.requireNonEmpty(questions, "plannedQuestions");
        List<PlannedQuestion> result = new ArrayList<>(questions);
        result.sort(java.util.Comparator.comparingInt(PlannedQuestion::position));
        for (int index = 0; index < result.size(); index++) {
            DomainPreconditions.require(result.get(index).position() == index + 1,
                    DomainErrorCode.INVALID_ARGUMENT, "planned question positions must be contiguous from one");
        }
        DomainPreconditions.require(new HashSet<>(result.stream()
                        .map(item -> item.questionVersion().resourceId()).toList()).size() == result.size(),
                DomainErrorCode.INVALID_ARGUMENT, "plan must not contain duplicate question versions");
        return List.copyOf(result);
    }
}
