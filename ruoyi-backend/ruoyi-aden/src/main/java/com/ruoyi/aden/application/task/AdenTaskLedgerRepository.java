package com.ruoyi.aden.application.task;

import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskStep;
import com.ruoyi.aden.domain.task.AdenTaskStepId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Optional;

public interface AdenTaskLedgerRepository {
    long lockWorkspaceEventSequence(AdenWorkspaceId workspaceId);

    Optional<InboxFact> findInboxForUpdate(AdenWorkspaceId workspaceId,
                                           String producerId,
                                           String operation,
                                           AdenIdempotencyKey key);

    void insertInbox(InboxFact fact);

    void completeInbox(AdenWorkspaceId workspaceId, String inboxId,
                       int responseStatus, String responseJson, Instant completedAt);

    void insertTask(AdenTask task, String inputJson,
                    AdenIdempotencyKey createKey, String requestHash);

    void insertStep(AdenWorkspaceId workspaceId, AdenTaskId taskId,
                    AdenTaskStep step, String inputJson, Instant occurredAt);

    Optional<StoredTask> findTaskForUpdate(AdenWorkspaceId workspaceId, AdenTaskId taskId);

    void updateValidationFailureCas(AdenTask previous, AdenTask next, String reasonCode);

    void markFirstStepReady(AdenWorkspaceId workspaceId, AdenTaskId taskId, Instant occurredAt);

    AdenTaskStepId findFirstStepIdForUpdate(AdenWorkspaceId workspaceId, AdenTaskId taskId);

    void insertReadyDelivery(AdenWorkspaceId workspaceId, String deliveryId, AdenTaskId taskId,
                             AdenTaskStepId stepId, String taskPackageJson,
                             String taskPackageHash, Instant occurredAt);

    void requestCancelDeliveries(AdenWorkspaceId workspaceId, AdenTaskId taskId, Instant occurredAt);

    void cancelReadySteps(AdenWorkspaceId workspaceId, AdenTaskId taskId, Instant occurredAt);

    int countActiveDeliveries(AdenWorkspaceId workspaceId, AdenTaskId taskId);

    void advanceWorkspaceEventSequence(AdenWorkspaceId workspaceId, long expected, long next, Instant occurredAt);

    void insertEvent(LedgerEvent event);

    void insertOutbox(OutboxMessage outbox);

    void insertAudit(TaskAudit audit);

    record InboxFact(
            AdenWorkspaceId workspaceId,
            String inboxId,
            String producerId,
            String operation,
            AdenIdempotencyKey key,
            String requestHash,
            String state,
            String responseJson,
            Instant inProgressUntil,
            Instant retentionUntil,
            Instant createdAt) { }

    record StoredTask(AdenTask task, String inputJson) { }

    record LedgerEvent(
            AdenWorkspaceId workspaceId,
            String eventId,
            long eventSequence,
            AdenTaskId taskId,
            long aggregateVersion,
            String eventType,
            String payloadJson,
            String actorType,
            String actorId,
            String correlationId,
            Instant occurredAt) { }

    record OutboxMessage(
            AdenWorkspaceId workspaceId,
            String outboxId,
            String eventId,
            String consumer,
            Instant availableAt) { }

    record TaskAudit(
            AdenWorkspaceId workspaceId,
            String auditEventId,
            String actionCode,
            AdenTaskId taskId,
            String actorType,
            String actorId,
            String correlationId,
            String detailsJson,
            Instant occurredAt) { }
}
