package com.aiinterviewcoach.domain.learning;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** LearningItem 权威状态；历史类名保留，不再表示自由文本任务。 */
public record LearningTask(
        ResourceId itemId,
        ResourceId questionVersionId,
        String questionTitle,
        String weaknessRef,
        List<String> reasonCodes,
        int priority,
        Optional<Instant> scheduledAt,
        LearningItemStatus state,
        AggregateVersion version
) {

    public LearningTask {
        DomainPreconditions.requireNonNull(itemId, "learningItemId");
        DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
        questionTitle = DomainPreconditions.requireText(questionTitle, "questionTitle");
        DomainPreconditions.require(questionTitle.length() <= 300, DomainErrorCode.INVALID_ARGUMENT,
                "questionTitle is too long");
        weaknessRef = DomainPreconditions.requireText(weaknessRef, "weaknessRef");
        DomainPreconditions.require(weaknessRef.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "weaknessRef is too long");
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        DomainPreconditions.require(reasonCodes.size() <= 8, DomainErrorCode.INVALID_ARGUMENT,
                "reason code count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(reasonCodes).size() == reasonCodes.size(),
                DomainErrorCode.INVALID_ARGUMENT, "reason codes must be unique");
        reasonCodes.forEach(value -> {
            DomainPreconditions.requireText(value, "reasonCode");
            DomainPreconditions.require(value.length() <= 96, DomainErrorCode.INVALID_ARGUMENT,
                    "reasonCode is too long");
        });
        DomainPreconditions.require(priority >= 1 && priority <= 5, DomainErrorCode.INVALID_ARGUMENT,
                "priority must be between one and five");
        scheduledAt = scheduledAt == null ? Optional.empty() : scheduledAt;
        DomainPreconditions.requireNonNull(state, "learningItemState");
        DomainPreconditions.requireNonNull(version, "learningItemVersion");
    }

    public LearningTask start() {
        DomainPreconditions.require(state == LearningItemStatus.PENDING, DomainErrorCode.INVALID_STATE,
                "only pending item can start");
        return copy(LearningItemStatus.IN_PROGRESS, scheduledAt);
    }

    public LearningTask complete() {
        DomainPreconditions.require(state == LearningItemStatus.PENDING || state == LearningItemStatus.IN_PROGRESS,
                DomainErrorCode.INVALID_STATE, "learning item cannot complete from current state");
        return copy(LearningItemStatus.COMPLETED, scheduledAt);
    }

    public LearningTask skip() {
        DomainPreconditions.require(state == LearningItemStatus.PENDING || state == LearningItemStatus.IN_PROGRESS,
                DomainErrorCode.INVALID_STATE, "learning item cannot skip from current state");
        return copy(LearningItemStatus.SKIPPED, scheduledAt);
    }

    public LearningTask reschedule(Instant at) {
        DomainPreconditions.require(state == LearningItemStatus.PENDING || state == LearningItemStatus.IN_PROGRESS,
                DomainErrorCode.INVALID_STATE, "learning item cannot be rescheduled from current state");
        return copy(LearningItemStatus.PENDING, Optional.of(DomainPreconditions.requireNonNull(at, "scheduledAt")));
    }

    public LearningTask cancel() {
        DomainPreconditions.require(!terminal(), DomainErrorCode.INVALID_STATE,
                "terminal learning item cannot be cancelled");
        return copy(LearningItemStatus.CANCELLED, scheduledAt);
    }

    public boolean terminal() {
        return state == LearningItemStatus.COMPLETED || state == LearningItemStatus.SKIPPED
                || state == LearningItemStatus.CANCELLED;
    }

    private LearningTask copy(LearningItemStatus next, Optional<Instant> nextSchedule) {
        return new LearningTask(itemId, questionVersionId, questionTitle, weaknessRef, reasonCodes,
                priority, nextSchedule, next, version.next());
    }

    @Override
    public String toString() {
        return "LearningTask[itemId=" + itemId + ", questionVersionId=" + questionVersionId
                + ", questionTitle=<redacted>, weaknessRef=<redacted>, state=" + state
                + ", version=" + version + "]";
    }
}
