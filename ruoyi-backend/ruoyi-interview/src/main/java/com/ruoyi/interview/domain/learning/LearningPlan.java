package com.ruoyi.interview.domain.learning;

import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 由受控 Learning Coach 候选固化的权威计划；外部命令不能直接提交 items。 */
public final class LearningPlan extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final ResourceId sourceReportId;
    private final ResourceId sourceReportVersionId;
    private final String configVersionId;
    private final PromptSchemaPin coachSchemaPin;
    private final ProviderPolicySnapshot providerPolicySnapshot;
    private final List<LearningTask> items;
    private final List<String> limitations;
    private LearningPlanStatus status;
    private Instant confirmedAt;
    private Instant completedAt;
    private AggregateVersion version;

    private LearningPlan(ResourceId id, TenantId tenantId, UserId userId,
                         ResourceId sourceReportId, ResourceId sourceReportVersionId,
                         String configVersionId,
                         PromptSchemaPin coachSchemaPin, ProviderPolicySnapshot providerPolicySnapshot,
                         List<LearningTask> items, List<String> limitations,
                         LearningPlanStatus status, Instant confirmedAt, Instant completedAt,
                         AggregateVersion version) {
        this.id = DomainPreconditions.requireNonNull(id, "learningPlanId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.sourceReportId = DomainPreconditions.requireNonNull(sourceReportId, "sourceReportId");
        this.sourceReportVersionId = DomainPreconditions.requireNonNull(sourceReportVersionId,
                "sourceReportVersionId");
        this.configVersionId = DomainPreconditions.requireText(configVersionId, "configVersionId");
        DomainPreconditions.require(this.configVersionId.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "configVersionId is too long");
        this.coachSchemaPin = DomainPreconditions.requireNonNull(coachSchemaPin, "coachSchemaPin");
        this.providerPolicySnapshot = DomainPreconditions.requireNonNull(providerPolicySnapshot,
                "providerPolicySnapshot");
        this.items = new ArrayList<>(items == null ? List.of() : items);
        DomainPreconditions.require(this.items.size() <= 12, DomainErrorCode.INVALID_ARGUMENT,
                "learning item count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(this.items.stream().map(LearningTask::itemId).toList()).size()
                        == this.items.size(), DomainErrorCode.INVALID_ARGUMENT,
                "learning item IDs must be unique");
        this.limitations = List.copyOf(limitations == null ? List.of() : limitations);
        DomainPreconditions.require(this.limitations.size() <= 16, DomainErrorCode.INVALID_ARGUMENT,
                "learning limitation count exceeds schema limit");
        this.limitations.forEach(value -> {
            DomainPreconditions.requireText(value, "learningLimitation");
            DomainPreconditions.require(value.length() <= 500, DomainErrorCode.INVALID_ARGUMENT,
                    "learning limitation is too long");
        });
        this.status = DomainPreconditions.requireNonNull(status, "learningPlanStatus");
        this.confirmedAt = confirmedAt;
        this.completedAt = completedAt;
        this.version = DomainPreconditions.requireNonNull(version, "learningPlanVersion");
        assertState();
    }

    public static LearningPlan candidate(ResourceId id, TenantId tenantId, UserId userId,
                                         ResourceId sourceReportId, ResourceId sourceReportVersionId,
                                         String configVersionId,
                                         PromptSchemaPin coachSchemaPin,
                                         ProviderPolicySnapshot providerPolicySnapshot,
                                         List<LearningTask> items, List<String> limitations,
                                         EventContext context) {
        LearningPlan plan = new LearningPlan(id, tenantId, userId, sourceReportId, sourceReportVersionId,
                configVersionId, coachSchemaPin, providerPolicySnapshot, items, limitations,
                LearningPlanStatus.CANDIDATE,
                null, null, AggregateVersion.initial());
        plan.recordEvent("learning.plan.candidate_created", tenantId, id, plan.version, context,
                Map.of("sourceReportId", sourceReportId.value(),
                        "sourceReportVersionId", sourceReportVersionId.value(),
                        "configVersionId", configVersionId,
                        "itemCount", Integer.toString(items.size())));
        return plan;
    }

    public static LearningPlan rehydrate(ResourceId id, TenantId tenantId, UserId userId,
                                         ResourceId sourceReportId, ResourceId sourceReportVersionId,
                                         String configVersionId,
                                         PromptSchemaPin coachSchemaPin,
                                         ProviderPolicySnapshot providerPolicySnapshot,
                                         List<LearningTask> items, List<String> limitations,
                                         LearningPlanStatus status, Instant confirmedAt,
                                         Instant completedAt, AggregateVersion version) {
        return new LearningPlan(id, tenantId, userId, sourceReportId, sourceReportVersionId,
                configVersionId, coachSchemaPin, providerPolicySnapshot, items, limitations, status,
                confirmedAt, completedAt, version);
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UserId userId() { return userId; }
    public ResourceId sourceReportId() { return sourceReportId; }
    public ResourceId sourceReportVersionId() { return sourceReportVersionId; }
    public String configVersionId() { return configVersionId; }
    public PromptSchemaPin coachSchemaPin() { return coachSchemaPin; }
    public ProviderPolicySnapshot providerPolicySnapshot() { return providerPolicySnapshot; }
    public List<LearningTask> items() { return List.copyOf(items); }
    public List<String> limitations() { return limitations; }
    public LearningPlanStatus status() { return status; }
    public Optional<Instant> confirmedAt() { return Optional.ofNullable(confirmedAt); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public AggregateVersion version() { return version; }

    public void confirm(AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == LearningPlanStatus.CANDIDATE, DomainErrorCode.INVALID_STATE,
                "only candidate plan can be confirmed");
        confirmedAt = context.occurredAt();
        if (items.isEmpty()) {
            status = LearningPlanStatus.COMPLETED;
            completedAt = context.occurredAt();
            bump("learning.plan.completed", context, Map.of("reasonCode", "NO_LEARNING_ITEMS"));
            return;
        }
        status = LearningPlanStatus.CONFIRMED;
        bump("learning.plan.confirmed", context, Map.of());
    }

    public void applyItemCommand(ResourceId itemId, LearningItemCommand command,
                                 Optional<Instant> scheduledAt, Optional<String> reasonCode,
                                 AggregateVersion expectedItemVersion,
                                 AggregateVersion expectedPlanVersion, EventContext context) {
        version.requireMatches(expectedPlanVersion);
        DomainPreconditions.require(status == LearningPlanStatus.CONFIRMED, DomainErrorCode.INVALID_STATE,
                "learning item command requires a confirmed plan");
        int index = findItem(itemId);
        LearningTask current = items.get(index);
        current.version().requireMatches(expectedItemVersion);
        LearningItemCommand checkedCommand = DomainPreconditions.requireNonNull(command, "learningItemCommand");
        Optional<String> checkedReasonCode = reasonCode == null ? Optional.empty() : reasonCode;
        checkedReasonCode = checkedReasonCode.map(value -> DomainPreconditions.requireText(value, "reasonCode"));
        checkedReasonCode.ifPresent(value -> DomainPreconditions.require(value.length() <= 96,
                DomainErrorCode.INVALID_ARGUMENT, "learning item reasonCode is too long"));
        LearningTask updated = switch (checkedCommand) {
            case COMPLETE -> current.complete();
            case SKIP -> current.skip();
            case RESCHEDULE -> current.reschedule(scheduledAt.orElseThrow(() ->
                    new com.ruoyi.interview.domain.platform.DomainException(
                            DomainErrorCode.INVALID_ARGUMENT, "reschedule requires scheduledAt")));
        };
        items.set(index, updated);
        boolean completesPlan = items.stream().allMatch(LearningTask::terminal);
        if (completesPlan) {
            status = LearningPlanStatus.COMPLETED;
            completedAt = context.occurredAt();
        }
        Map<String, String> attributes = new java.util.LinkedHashMap<>();
        attributes.put("itemId", itemId.value());
        attributes.put("itemVersion", Long.toString(updated.version().value()));
        checkedReasonCode.ifPresent(value -> {
            if (value.matches("[A-Z][A-Z0-9_]{0,95}")) {
                attributes.put("reasonCode", value);
            } else {
                attributes.put("reasonCodeProvided", "true");
            }
        });
        bump("learning.item." + checkedCommand.name().toLowerCase(java.util.Locale.ROOT), context,
                Map.copyOf(attributes));
        if (completesPlan) {
            bump("learning.plan.completed", context, Map.of());
        }
    }

    public void cancel(AggregateVersion expectedVersion, EventContext context) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(status == LearningPlanStatus.CANDIDATE
                        || status == LearningPlanStatus.CONFIRMED,
                DomainErrorCode.INVALID_STATE, "learning plan cannot be cancelled from current state");
        for (int index = 0; index < items.size(); index++) {
            if (!items.get(index).terminal()) {
                items.set(index, items.get(index).cancel());
            }
        }
        status = LearningPlanStatus.CANCELLED;
        bump("learning.plan.cancelled", context, Map.of());
    }

    public LearningProgressSnapshot progressSnapshot() {
        int completed = (int) items.stream().filter(item -> item.state() == LearningItemStatus.COMPLETED).count();
        return new LearningProgressSnapshot(items.size(), completed, 0);
    }

    private int findItem(ResourceId itemId) {
        DomainPreconditions.requireNonNull(itemId, "learningItemId");
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).itemId().equals(itemId)) {
                return index;
            }
        }
        throw new com.ruoyi.interview.domain.platform.DomainException(
                DomainErrorCode.INVALID_ARGUMENT, "learning item was not found");
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
        assertState();
    }

    private void assertState() {
        if (status == LearningPlanStatus.CONFIRMED || status == LearningPlanStatus.COMPLETED) {
            DomainPreconditions.require(confirmedAt != null, DomainErrorCode.INVALID_STATE,
                    "confirmed/completed plan requires confirmedAt");
        }
        DomainPreconditions.require((status == LearningPlanStatus.COMPLETED) == (completedAt != null),
                DomainErrorCode.INVALID_STATE,
                "completedAt is inconsistent with learning plan state");
        if (status == LearningPlanStatus.CANDIDATE) {
            DomainPreconditions.require(confirmedAt == null
                            && items.stream().allMatch(item -> item.state() == LearningItemStatus.PENDING),
                    DomainErrorCode.INVALID_STATE,
                    "candidate plan requires pending items and no confirmation");
        }
        if (status == LearningPlanStatus.CONFIRMED) {
            DomainPreconditions.require(!items.isEmpty()
                            && items.stream().anyMatch(item -> !item.terminal()),
                    DomainErrorCode.INVALID_STATE,
                    "confirmed plan requires a non-terminal item");
        }
        if (status == LearningPlanStatus.COMPLETED) {
            DomainPreconditions.require(completedAt != null && items.stream().allMatch(LearningTask::terminal),
                    DomainErrorCode.INVALID_STATE, "completed plan requires terminal items");
        }
        if (status == LearningPlanStatus.CANCELLED) {
            DomainPreconditions.require(items.stream().allMatch(LearningTask::terminal),
                    DomainErrorCode.INVALID_STATE, "cancelled plan requires terminal items");
        }
    }

    @Override
    public String toString() {
        return "LearningPlan[id=" + id + ", tenantId=" + tenantId + ", userId=" + userId
                + ", sourceReportId=" + sourceReportId + ", sourceReportVersionId=" + sourceReportVersionId
                + ", state=" + status + ", itemCount=" + items.size() + ", content=<redacted>]";
    }
}
