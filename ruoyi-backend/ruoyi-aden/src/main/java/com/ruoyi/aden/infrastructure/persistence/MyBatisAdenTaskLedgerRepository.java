package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.error.AdenNotFoundException;
import com.ruoyi.aden.application.error.AdenVersionConflictException;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.task.AdenTaskLedgerRepository;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenCorrelationId;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskStep;
import com.ruoyi.aden.domain.task.AdenTaskStepId;
import com.ruoyi.aden.domain.task.AdenTaskType;
import com.ruoyi.aden.domain.task.AdenTaskVersion;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenInboxRow;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenTaskMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenTaskPersistenceRow;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisAdenTaskLedgerRepository implements AdenTaskLedgerRepository {
    private final AdenTaskMapper mapper;

    public MyBatisAdenTaskLedgerRepository(AdenTaskMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public long lockWorkspaceEventSequence(AdenWorkspaceId workspaceId) {
        Long value = mapper.lockWorkspaceEventSequence(workspaceId.value());
        if (value == null) throw new AdenNotFoundException();
        return value;
    }

    @Override
    public Optional<InboxFact> findInboxForUpdate(AdenWorkspaceId workspaceId, String producerId,
                                                   String operation, AdenIdempotencyKey key) {
        return Optional.ofNullable(mapper.selectInboxForUpdate(
                workspaceId.value(), producerId, operation, key.value())).map(this::toInbox);
    }

    @Override
    public void insertInbox(InboxFact fact) {
        requireOne(mapper.insertInbox(
                fact.workspaceId().value(), fact.inboxId(), fact.producerId(), fact.operation(),
                fact.key().value(), fact.requestHash(), AdenUtcDateTimeCodec.toDatabase(fact.inProgressUntil()),
                AdenUtcDateTimeCodec.toDatabase(fact.retentionUntil()),
                AdenUtcDateTimeCodec.toDatabase(fact.createdAt())), "inbox");
    }

    @Override
    public void completeInbox(AdenWorkspaceId workspaceId, String inboxId, int responseStatus,
                              String responseJson, Instant completedAt) {
        requireOne(mapper.completeInbox(workspaceId.value(), inboxId, responseStatus, responseJson,
                AdenUtcDateTimeCodec.toDatabase(completedAt)), "complete inbox");
    }

    @Override
    public void insertTask(AdenTask task, String inputJson, AdenIdempotencyKey createKey,
                           String requestHash) {
        requireOne(mapper.insertTask(
                task.workspaceId().value(), task.id().value(), task.type().name(), task.capability().name(),
                task.state().name(), task.correlationId().value(), task.title(), inputJson,
                task.version().value(), createKey.value(), requestHash, task.createdByRuoYiUserId(),
                AdenUtcDateTimeCodec.toDatabase(task.createdAt()),
                AdenUtcDateTimeCodec.toDatabase(task.updatedAt())), "task");
    }

    @Override
    public void insertStep(AdenWorkspaceId workspaceId, AdenTaskId taskId, AdenTaskStep step,
                           String inputJson, Instant occurredAt) {
        requireOne(mapper.insertStep(workspaceId.value(), step.id().value(), taskId.value(),
                step.ordinal(), step.attemptNo(), step.state().name(), inputJson, step.version(),
                AdenUtcDateTimeCodec.toDatabase(occurredAt), AdenUtcDateTimeCodec.toDatabase(occurredAt)),
                "task step");
    }

    @Override
    public Optional<StoredTask> findTaskForUpdate(AdenWorkspaceId workspaceId, AdenTaskId taskId) {
        return Optional.ofNullable(mapper.selectTaskForUpdate(workspaceId.value(), taskId.value()))
                .map(this::toStoredTask);
    }

    @Override
    public void updateValidationFailureCas(AdenTask previous, AdenTask next, String reasonCode) {
        if (next.state() != AdenTaskState.FAILED
                || next.version().value() != previous.version().value() + 1
                || !next.workspaceId().equals(previous.workspaceId())
                || !next.id().equals(previous.id())) {
            throw new IllegalArgumentException("validation failure CAS 前后事实不一致");
        }
        int rows = mapper.updateValidationFailureCas(
                previous.workspaceId().value(), previous.id().value(), previous.version().value(),
                previous.state().name(), next.version().value(), reasonCode,
                AdenUtcDateTimeCodec.toDatabase(next.updatedAt()));
        if (rows != 1) {
            throw new AdenVersionConflictException(
                    Long.toString(previous.version().value()), previous.state().name());
        }
    }

    @Override
    public void markFirstStepReady(AdenWorkspaceId workspaceId, AdenTaskId taskId, Instant occurredAt) {
        requireOne(mapper.markFirstStepReady(
                workspaceId.value(), taskId.value(), AdenUtcDateTimeCodec.toDatabase(occurredAt)),
                "mark first step ready");
    }

    @Override
    public AdenTaskStepId findFirstStepIdForUpdate(AdenWorkspaceId workspaceId, AdenTaskId taskId) {
        String value = mapper.selectFirstStepIdForUpdate(workspaceId.value(), taskId.value());
        if (value == null) throw new IllegalStateException("Task 首步骤不存在");
        return new AdenTaskStepId(value);
    }

    @Override
    public void insertReadyDelivery(AdenWorkspaceId workspaceId, String deliveryId,
                                    AdenTaskId taskId, AdenTaskStepId stepId,
                                    String taskPackageJson, String taskPackageHash, Instant occurredAt) {
        requireOne(mapper.insertReadyDelivery(workspaceId.value(), deliveryId, taskId.value(),
                stepId.value(), taskPackageJson, taskPackageHash,
                AdenUtcDateTimeCodec.toDatabase(occurredAt)), "ready delivery");
    }

    @Override
    public void requestCancelDeliveries(AdenWorkspaceId workspaceId, AdenTaskId taskId, Instant occurredAt) {
        mapper.requestCancelDeliveries(workspaceId.value(), taskId.value(),
                AdenUtcDateTimeCodec.toDatabase(occurredAt));
    }

    @Override
    public void cancelReadySteps(AdenWorkspaceId workspaceId, AdenTaskId taskId, Instant occurredAt) {
        mapper.cancelReadySteps(workspaceId.value(), taskId.value(),
                AdenUtcDateTimeCodec.toDatabase(occurredAt));
    }

    @Override
    public int countActiveDeliveries(AdenWorkspaceId workspaceId, AdenTaskId taskId) {
        return mapper.countActiveDeliveries(workspaceId.value(), taskId.value());
    }

    @Override
    public void advanceWorkspaceEventSequence(AdenWorkspaceId workspaceId, long expected,
                                              long next, Instant occurredAt) {
        if (expected < 0 || expected == Long.MAX_VALUE || next != expected + 1) {
            throw new IllegalArgumentException("Workspace event sequence 必须连续增加 1");
        }
        requireOne(mapper.advanceWorkspaceEventSequence(
                workspaceId.value(), expected, next, AdenUtcDateTimeCodec.toDatabase(occurredAt)),
                "workspace event sequence");
    }

    @Override
    public void insertEvent(LedgerEvent event) {
        requireOne(mapper.insertEvent(
                event.workspaceId().value(), event.eventId(), event.eventSequence(), event.taskId().value(),
                event.aggregateVersion(), event.eventType(), event.payloadJson(), event.correlationId(),
                event.actorType(), event.actorId(), AdenUtcDateTimeCodec.toDatabase(event.occurredAt()),
                AdenUtcDateTimeCodec.toDatabase(event.occurredAt())), "event");
    }

    @Override
    public void insertOutbox(OutboxMessage outbox) {
        requireOne(mapper.insertOutbox(
                outbox.workspaceId().value(), outbox.outboxId(), outbox.eventId(), outbox.consumer(),
                AdenUtcDateTimeCodec.toDatabase(outbox.availableAt()),
                AdenUtcDateTimeCodec.toDatabase(outbox.availableAt()),
                AdenUtcDateTimeCodec.toDatabase(outbox.availableAt())), "outbox");
    }

    @Override
    public void insertAudit(TaskAudit audit) {
        requireOne(mapper.insertTaskAudit(
                audit.workspaceId().value(), audit.auditEventId(), audit.actionCode(), audit.taskId().value(),
                audit.actorType(), audit.actorId(), audit.correlationId(), audit.detailsJson(),
                AdenUtcDateTimeCodec.toDatabase(audit.occurredAt()),
                AdenUtcDateTimeCodec.toDatabase(audit.occurredAt())), "task audit");
    }

    private InboxFact toInbox(AdenInboxRow row) {
        return new InboxFact(new AdenWorkspaceId(row.getWorkspaceId()), row.getInboxId(),
                row.getProducerId(), row.getOperation(), new AdenIdempotencyKey(row.getIdempotencyKey()),
                row.getRequestHash(), row.getInboxState(), row.getResponseJson(),
                row.getInProgressUntil() == null ? null
                        : AdenUtcDateTimeCodec.fromDatabase(row.getInProgressUntil()),
                AdenUtcDateTimeCodec.fromDatabase(row.getRetentionUntil()),
                AdenUtcDateTimeCodec.fromDatabase(row.getCreatedAt()));
    }

    private StoredTask toStoredTask(AdenTaskPersistenceRow row) {
        AdenTask task = new AdenTask(
                new AdenWorkspaceId(row.getWorkspaceId()), new AdenTaskId(row.getTaskId()),
                AdenTaskType.valueOf(row.getTaskType()), AdenCapabilityCode.valueOf(row.getRequiredCapability()),
                row.getTitle(), AdenTaskState.valueOf(row.getTaskState()), new AdenTaskVersion(row.getVersion()),
                new AdenCorrelationId(row.getCorrelationId()), row.getCreatedByRuoYiUserId(),
                AdenUtcDateTimeCodec.fromDatabase(row.getCreatedAt()),
                AdenUtcDateTimeCodec.fromDatabase(row.getUpdatedAt()));
        return new StoredTask(task, row.getInputJson());
    }

    private static void requireOne(int rows, String operation) {
        if (rows != 1) throw new IllegalStateException(operation + " 写入行数必须为 1，实际为 " + rows);
    }
}
