package com.ruoyi.aden.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface AdenTaskMapper {
    Long lockWorkspaceEventSequence(@Param("workspaceId") String workspaceId);

    AdenInboxRow selectInboxForUpdate(@Param("workspaceId") String workspaceId,
                                      @Param("producerId") String producerId,
                                      @Param("operation") String operation,
                                      @Param("idempotencyKey") String idempotencyKey);

    int insertInbox(@Param("workspaceId") String workspaceId,
                    @Param("inboxId") String inboxId,
                    @Param("producerId") String producerId,
                    @Param("operation") String operation,
                    @Param("idempotencyKey") String idempotencyKey,
                    @Param("requestHash") String requestHash,
                    @Param("inProgressUntil") LocalDateTime inProgressUntil,
                    @Param("retentionUntil") LocalDateTime retentionUntil,
                    @Param("createdAt") LocalDateTime createdAt);

    int completeInbox(@Param("workspaceId") String workspaceId,
                      @Param("inboxId") String inboxId,
                      @Param("responseStatus") int responseStatus,
                      @Param("responseJson") String responseJson,
                      @Param("completedAt") LocalDateTime completedAt);

    int insertTask(@Param("workspaceId") String workspaceId,
                   @Param("taskId") String taskId,
                   @Param("taskType") String taskType,
                   @Param("requiredCapability") String requiredCapability,
                   @Param("taskState") String taskState,
                   @Param("correlationId") String correlationId,
                   @Param("title") String title,
                   @Param("inputJson") String inputJson,
                   @Param("version") long version,
                   @Param("createIdempotencyKey") String createIdempotencyKey,
                   @Param("createRequestHash") String createRequestHash,
                   @Param("createdByRuoYiUserId") long createdByRuoYiUserId,
                   @Param("createdAt") LocalDateTime createdAt,
                   @Param("updatedAt") LocalDateTime updatedAt);

    int insertStep(@Param("workspaceId") String workspaceId,
                   @Param("stepId") String stepId,
                   @Param("taskId") String taskId,
                   @Param("stepNo") int stepNo,
                   @Param("attemptNo") int attemptNo,
                   @Param("stepState") String stepState,
                   @Param("inputJson") String inputJson,
                   @Param("version") long version,
                   @Param("createdAt") LocalDateTime createdAt,
                   @Param("updatedAt") LocalDateTime updatedAt);

    AdenTaskPersistenceRow selectTaskForUpdate(@Param("workspaceId") String workspaceId,
                                                @Param("taskId") String taskId);

    int updateStateCas(@Param("workspaceId") String workspaceId,
                       @Param("taskId") String taskId,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("expectedState") String expectedState,
                       @Param("nextVersion") long nextVersion,
                       @Param("nextState") String nextState,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int updateValidationFailureCas(@Param("workspaceId") String workspaceId,
                                   @Param("taskId") String taskId,
                                   @Param("expectedVersion") long expectedVersion,
                                   @Param("expectedState") String expectedState,
                                   @Param("nextVersion") long nextVersion,
                                   @Param("reasonCode") String reasonCode,
                                   @Param("updatedAt") LocalDateTime updatedAt);

    int markFirstStepReady(@Param("workspaceId") String workspaceId,
                           @Param("taskId") String taskId,
                           @Param("updatedAt") LocalDateTime updatedAt);

    String selectFirstStepIdForUpdate(@Param("workspaceId") String workspaceId,
                                      @Param("taskId") String taskId);

    int insertReadyDelivery(@Param("workspaceId") String workspaceId,
                            @Param("deliveryId") String deliveryId,
                            @Param("taskId") String taskId,
                            @Param("stepId") String stepId,
                            @Param("taskPackageJson") String taskPackageJson,
                            @Param("taskPackageHash") String taskPackageHash,
                            @Param("now") LocalDateTime now);

    int requestCancelDeliveries(@Param("workspaceId") String workspaceId,
                                @Param("taskId") String taskId,
                                @Param("now") LocalDateTime now);

    int cancelReadySteps(@Param("workspaceId") String workspaceId,
                         @Param("taskId") String taskId,
                         @Param("now") LocalDateTime now);

    int countActiveDeliveries(@Param("workspaceId") String workspaceId,
                              @Param("taskId") String taskId);

    int advanceWorkspaceEventSequence(@Param("workspaceId") String workspaceId,
                                      @Param("expected") long expected,
                                      @Param("next") long next,
                                      @Param("updatedAt") LocalDateTime updatedAt);

    int insertEvent(@Param("workspaceId") String workspaceId,
                    @Param("eventId") String eventId,
                    @Param("eventSequence") long eventSequence,
                    @Param("aggregateId") String aggregateId,
                    @Param("aggregateVersion") long aggregateVersion,
                    @Param("eventType") String eventType,
                    @Param("payloadJson") String payloadJson,
                    @Param("correlationId") String correlationId,
                    @Param("actorType") String actorType,
                    @Param("actorId") String actorId,
                    @Param("occurredAt") LocalDateTime occurredAt,
                    @Param("createdAt") LocalDateTime createdAt);

    int insertOutbox(@Param("workspaceId") String workspaceId,
                     @Param("outboxId") String outboxId,
                     @Param("eventId") String eventId,
                     @Param("consumer") String consumer,
                     @Param("availableAt") LocalDateTime availableAt,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int insertTaskAudit(@Param("workspaceId") String workspaceId,
                        @Param("auditEventId") String auditEventId,
                        @Param("actionCode") String actionCode,
                        @Param("taskId") String taskId,
                        @Param("actorType") String actorType,
                        @Param("actorId") String actorId,
                        @Param("correlationId") String correlationId,
                        @Param("detailsJson") String detailsJson,
                        @Param("occurredAt") LocalDateTime occurredAt,
                        @Param("createdAt") LocalDateTime createdAt);
}
