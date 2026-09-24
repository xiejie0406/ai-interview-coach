package com.ruoyi.aden.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AdenRunnerDeliveryMapper {
    Long lockWorkspace(@Param("workspaceId")String workspaceId);
    AdenInboxRow selectClaimInboxForUpdate(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId,@Param("key")String key);
    int insertClaimInbox(@Param("workspaceId")String workspaceId,@Param("inboxId")String inboxId,@Param("sessionId")String sessionId,@Param("key")String key,@Param("hash")String hash,@Param("inProgressUntil")LocalDateTime inProgressUntil,@Param("retentionUntil")LocalDateTime retentionUntil,@Param("now")LocalDateTime now);
    int completeClaimInbox(@Param("workspaceId")String workspaceId,@Param("inboxId")String inboxId,@Param("response")String response,@Param("now")LocalDateTime now);
    AdenRunnerSessionRow selectSessionForUpdate(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId);
    List<AdenDeliveryRow> selectReadyForUpdate(@Param("workspaceId")String workspaceId,@Param("capability")String capability,@Param("limit")int limit);
    int claimDelivery(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("expectedVersion")long expectedVersion,@Param("leaseTtlSeconds")int leaseTtlSeconds);
    AdenDeliveryRow selectClaimed(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch);
    int increaseInFlight(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("count")int count,@Param("expectedVersion")long expectedVersion);
    int countLiveClaim(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("fence")long fence);
    int heartbeatSession(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("sequence")long sequence,@Param("expectedVersion")long expectedVersion,@Param("sessionTtlSeconds")int sessionTtlSeconds);
    List<String> selectCancelRequested(@Param("workspaceId")String workspaceId,
                                       @Param("sessionId")String sessionId,
                                       @Param("sessionEpoch")long sessionEpoch,
                                       @Param("deliveryIds")List<String> deliveryIds);
    int heartbeatDelivery(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("fence")long fence,@Param("sequence")long sequence,@Param("leaseTtlSeconds")int leaseTtlSeconds);
    AdenDeliveryRow selectHeartbeatDelivery(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("fence")long fence);
    AdenInboxRow selectReceiptInboxForUpdate(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId,@Param("key")String key);
    int insertReceiptInbox(@Param("workspaceId")String workspaceId,@Param("inboxId")String inboxId,@Param("sessionId")String sessionId,@Param("key")String key,@Param("hash")String hash,@Param("inProgressUntil")LocalDateTime inProgressUntil,@Param("retentionUntil")LocalDateTime retentionUntil,@Param("now")LocalDateTime now);
    int completeReceiptInbox(@Param("workspaceId")String workspaceId,@Param("inboxId")String inboxId,@Param("response")String response,@Param("now")LocalDateTime now);
    AdenDeliveryRow selectDeliveryRoute(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId);
    AdenTaskStepRow selectReceiptStepForUpdate(@Param("workspaceId")String workspaceId,@Param("stepId")String stepId);
    AdenDeliveryRow selectReceiptDeliveryForUpdate(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId);
    int applyReceiptDelivery(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("expectedState")String expectedState,@Param("nextState")String nextState,@Param("expectedVersion")long expectedVersion,@Param("receiptSequence")long receiptSequence,@Param("outcomeJson")String outcomeJson,@Param("errorCode")String errorCode,@Param("errorMessage")String errorMessage);
    int failStartedForRetry(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,
                            @Param("expectedVersion")long expectedVersion,
                            @Param("receiptSequence")long receiptSequence,
                            @Param("outcomeJson")String outcomeJson,
                            @Param("errorCode")String errorCode,
                            @Param("errorMessage")String errorMessage);
    int applyReceiptStep(@Param("workspaceId")String workspaceId,@Param("stepId")String stepId,@Param("expectedState")String expectedState,@Param("nextState")String nextState,@Param("expectedVersion")long expectedVersion,@Param("outputJson")String outputJson,@Param("errorCode")String errorCode,@Param("errorMessage")String errorMessage);
    int decreaseInFlight(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId,@Param("sessionEpoch")long sessionEpoch,@Param("count")int count);
    List<AdenDeliveryRow> selectExpiredRoutes(@Param("workspaceId")String workspaceId,@Param("limit")int limit);
    int requeueExpiredUnstarted(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("expectedVersion")long expectedVersion);
    int expireAsOutcomeUnknown(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("expectedVersion")long expectedVersion);
    int failExpiredStarted(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("expectedVersion")long expectedVersion);
    int insertRetryDelivery(@Param("workspaceId")String workspaceId,@Param("deliveryId")String deliveryId,@Param("taskId")String taskId,@Param("stepId")String stepId,@Param("attemptNo")int attemptNo,@Param("taskPackageJson")String taskPackageJson,@Param("taskPackageHash")String taskPackageHash);
    int updateRecoveryStep(@Param("workspaceId")String workspaceId,@Param("stepId")String stepId,@Param("expectedVersion")long expectedVersion,@Param("nextState")String nextState,@Param("errorCode")String errorCode);
}
