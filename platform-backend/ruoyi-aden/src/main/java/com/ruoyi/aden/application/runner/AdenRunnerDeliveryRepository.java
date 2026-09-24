package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AdenRunnerDeliveryRepository {
    long lockWorkspace(AdenWorkspaceId workspaceId);
    Optional<ClaimInbox> findClaimInboxForUpdate(AdenWorkspaceId workspaceId,AdenSessionId sessionId,AdenIdempotencyKey key);
    void insertClaimInbox(ClaimInbox inbox);
    void completeClaimInbox(AdenWorkspaceId workspaceId,String inboxId,String responseJson,Instant now);
    Optional<SessionCapacity> lockSession(AdenRunnerSessionPrincipal principal);
    List<ClaimCandidate> lockReady(AdenWorkspaceId workspaceId,AdenCapabilityCode capability,int limit);
    ClaimedDelivery claim(ClaimCandidate candidate,AdenRunnerSessionPrincipal principal,int leaseTtlSeconds);
    void increaseInFlight(SessionCapacity session,int count);
    boolean isLive(ClaimedDelivery delivery);
    void heartbeatSession(SessionCapacity session,long sequence,int sessionTtlSeconds);
    List<AdenDeliveryId> findCancelRequested(AdenRunnerSessionPrincipal principal,
                                             List<AdenDeliveryId> activeDeliveryIds);
    Optional<HeartbeatDelivery> heartbeatDelivery(AdenRunnerSessionPrincipal principal,AdenDeliveryId deliveryId,long fence,long sequence,int leaseTtlSeconds);
    Optional<ClaimInbox> findReceiptInboxForUpdate(AdenWorkspaceId workspaceId,AdenSessionId sessionId,AdenIdempotencyKey key);
    void insertReceiptInbox(ClaimInbox inbox);
    void completeReceiptInbox(AdenWorkspaceId workspaceId,String inboxId,String responseJson,Instant now);
    Optional<ReceiptRoute> findReceiptRoute(AdenWorkspaceId workspaceId,AdenDeliveryId deliveryId);
    Optional<ReceiptStep> lockReceiptStep(AdenWorkspaceId workspaceId,String stepId);
    Optional<ReceiptDelivery> lockReceiptDelivery(AdenWorkspaceId workspaceId,AdenDeliveryId deliveryId);
    void applyReceiptDelivery(ReceiptDelivery delivery,String nextState,long receiptSequence,String outcomeJson,String errorCode,String errorMessage);
    void retryAfterReceipt(ReceiptDelivery delivery,String newDeliveryId,String taskPackageJson,
                           String taskPackageHash,long receiptSequence,String outcomeJson,
                           String errorCode,String errorMessage);
    void applyReceiptStep(ReceiptStep step,String nextState,String outputJson,String errorCode,String errorMessage);
    void decreaseInFlight(AdenRunnerSessionPrincipal principal,int count);
    List<RecoveryRoute> findExpiredRoutes(AdenWorkspaceId workspaceId,int limit);
    void requeueExpiredUnstarted(ReceiptDelivery delivery);
    void expireAsOutcomeUnknown(ReceiptDelivery delivery);
    void retryExpiredStarted(ReceiptDelivery delivery,String newDeliveryId,String taskPackageJson,String taskPackageHash);
    void updateRecoveryStep(ReceiptStep step,String nextState,String errorCode);

    record ClaimInbox(AdenWorkspaceId workspaceId,String inboxId,AdenSessionId sessionId,AdenIdempotencyKey key,String requestHash,String state,String responseJson,Instant inProgressUntil,Instant retentionUntil,Instant createdAt){}
    record SessionCapacity(AdenWorkspaceId workspaceId,AdenSessionId sessionId,long epoch,long runnerCurrentEpoch,String status,int capacity,int inFlight,long version,Instant expiresAt,Instant databaseNow){public int remaining(){return capacity-inFlight;}}
    record ClaimCandidate(AdenWorkspaceId workspaceId,AdenDeliveryId deliveryId,long version){}
    record ClaimedDelivery(AdenWorkspaceId workspaceId,AdenDeliveryId deliveryId,String taskId,String stepId,int attemptNo,long fenceToken,Instant leaseUntil,String taskPackageJson,String taskPackageHash,AdenSessionId sessionId,long sessionEpoch){}
    record HeartbeatDelivery(AdenDeliveryId deliveryId,String state,long fenceToken,
                             long heartbeatSequence,Instant leaseUntil,
                             boolean cancelRequested,Instant databaseNow){}
    record ReceiptRoute(String taskId,String stepId){}
    record ReceiptStep(AdenWorkspaceId workspaceId,String taskId,String stepId,String state,int attemptNo,long version){}
    record ReceiptDelivery(AdenWorkspaceId workspaceId,AdenDeliveryId deliveryId,String taskId,String stepId,String state,int attemptNo,
                           String taskPackageJson,String taskPackageHash,
                           String ownerSessionId,long ownerSessionEpoch,long fenceToken,
                           long latestReceiptSequence,Instant leaseUntil,Instant cancelRequestedAt,
                           Instant databaseNow,long version){}
    record RecoveryRoute(String runnerId,String sessionId,long sessionEpoch,String taskId,String stepId,AdenDeliveryId deliveryId){}
}
