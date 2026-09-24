package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.runner.AdenRunnerDeliveryRepository;
import com.ruoyi.aden.application.runner.AdenRunnerSessionPrincipal;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenDeliveryRow;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenInboxRow;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerDeliveryMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerSessionRow;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenTaskStepRow;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Runner 拉取投递的 MySQL 账本适配器；所有竞争性写入均要求恰好更新一行。 */
public final class MyBatisAdenRunnerDeliveryRepository implements AdenRunnerDeliveryRepository {
    private final AdenRunnerDeliveryMapper mapper;

    public MyBatisAdenRunnerDeliveryRepository(AdenRunnerDeliveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long lockWorkspace(AdenWorkspaceId workspaceId) {
        Long sequence = mapper.lockWorkspace(workspaceId.value());
        if (sequence == null) {
            throw new IllegalStateException("Workspace 不存在或不可用");
        }
        return sequence;
    }

    @Override
    public Optional<ClaimInbox> findClaimInboxForUpdate(AdenWorkspaceId workspaceId,
                                                        AdenSessionId sessionId,
                                                        AdenIdempotencyKey key) {
        return Optional.ofNullable(mapper.selectClaimInboxForUpdate(
                workspaceId.value(), sessionId.value(), key.value())).map(this::inbox);
    }

    @Override
    public void insertClaimInbox(ClaimInbox inbox) {
        one(mapper.insertClaimInbox(inbox.workspaceId().value(), inbox.inboxId(),
                inbox.sessionId().value(), inbox.key().value(), inbox.requestHash(),
                dt(inbox.inProgressUntil()), dt(inbox.retentionUntil()), dt(inbox.createdAt())),
                "insert claim inbox");
    }

    @Override
    public void completeClaimInbox(AdenWorkspaceId workspaceId, String inboxId,
                                   String responseJson, Instant now) {
        one(mapper.completeClaimInbox(workspaceId.value(), inboxId, responseJson, dt(now)),
                "complete claim inbox");
    }

    @Override
    public Optional<SessionCapacity> lockSession(AdenRunnerSessionPrincipal principal) {
        return Optional.ofNullable(mapper.selectSessionForUpdate(
                principal.workspaceId().value(), principal.sessionId().value())).map(this::session);
    }

    @Override
    public List<ClaimCandidate> lockReady(AdenWorkspaceId workspaceId,
                                          AdenCapabilityCode capability, int limit) {
        return mapper.selectReadyForUpdate(workspaceId.value(), capability.name(), limit)
                .stream().map(row -> new ClaimCandidate(workspaceId,
                        new AdenDeliveryId(row.getDeliveryId()), row.getVersion())).toList();
    }

    @Override
    public ClaimedDelivery claim(ClaimCandidate candidate, AdenRunnerSessionPrincipal principal,
                                 int leaseTtlSeconds) {
        one(mapper.claimDelivery(candidate.workspaceId().value(), candidate.deliveryId().value(),
                principal.sessionId().value(), principal.sessionEpoch(), candidate.version(),
                leaseTtlSeconds), "claim delivery");
        AdenDeliveryRow row = mapper.selectClaimed(candidate.workspaceId().value(),
                candidate.deliveryId().value(), principal.sessionId().value(),
                principal.sessionEpoch());
        if (row == null) throw new IllegalStateException("Claim 后无法读取 Delivery");
        return delivery(row);
    }

    @Override
    public void increaseInFlight(SessionCapacity session, int count) {
        one(mapper.increaseInFlight(session.workspaceId().value(), session.sessionId().value(),
                session.epoch(), count, session.version()), "increase in-flight");
    }

    @Override
    public boolean isLive(ClaimedDelivery delivery) {
        return mapper.countLiveClaim(delivery.workspaceId().value(), delivery.deliveryId().value(),
                delivery.sessionId().value(), delivery.sessionEpoch(), delivery.fenceToken()) == 1;
    }

    @Override
    public void heartbeatSession(SessionCapacity session, long sequence, int sessionTtlSeconds) {
        one(mapper.heartbeatSession(session.workspaceId().value(), session.sessionId().value(),
                session.epoch(), sequence, session.version(), sessionTtlSeconds),
                "heartbeat session");
    }

    @Override
    public List<AdenDeliveryId> findCancelRequested(AdenRunnerSessionPrincipal principal,
                                                    List<AdenDeliveryId> activeDeliveryIds) {
        if (activeDeliveryIds.isEmpty()) return List.of();
        return mapper.selectCancelRequested(principal.workspaceId().value(),
                        principal.sessionId().value(), principal.sessionEpoch(),
                        activeDeliveryIds.stream().map(AdenDeliveryId::value).toList())
                .stream().map(AdenDeliveryId::new).toList();
    }

    @Override
    public Optional<HeartbeatDelivery> heartbeatDelivery(AdenRunnerSessionPrincipal principal,
                                                          AdenDeliveryId deliveryId,
                                                          long fence, long sequence,
                                                          int leaseTtlSeconds) {
        int updated = mapper.heartbeatDelivery(principal.workspaceId().value(), deliveryId.value(),
                principal.sessionId().value(), principal.sessionEpoch(), fence, sequence,
                leaseTtlSeconds);
        if (updated == 0) return Optional.empty();
        one(updated, "heartbeat delivery");
        AdenDeliveryRow row = mapper.selectHeartbeatDelivery(principal.workspaceId().value(),
                deliveryId.value(), principal.sessionId().value(), principal.sessionEpoch(), fence);
        if (row == null) throw new IllegalStateException("Heartbeat 后无法读取 Delivery");
        return Optional.of(new HeartbeatDelivery(deliveryId, row.getDeliveryState(),
                row.getFenceToken(), row.getHeartbeatSequence(), instant(row.getLeaseUntil()),
                row.getCancelRequestedAt() != null, instant(row.getDatabaseNow())));
    }

    @Override
    public Optional<ClaimInbox> findReceiptInboxForUpdate(AdenWorkspaceId workspaceId,
                                                          AdenSessionId sessionId,
                                                          AdenIdempotencyKey key) {
        return Optional.ofNullable(mapper.selectReceiptInboxForUpdate(
                workspaceId.value(), sessionId.value(), key.value())).map(this::inbox);
    }

    @Override
    public void insertReceiptInbox(ClaimInbox inbox) {
        one(mapper.insertReceiptInbox(inbox.workspaceId().value(), inbox.inboxId(),
                inbox.sessionId().value(), inbox.key().value(), inbox.requestHash(),
                dt(inbox.inProgressUntil()), dt(inbox.retentionUntil()), dt(inbox.createdAt())),
                "insert receipt inbox");
    }

    @Override
    public void completeReceiptInbox(AdenWorkspaceId workspaceId, String inboxId,
                                     String responseJson, Instant now) {
        one(mapper.completeReceiptInbox(workspaceId.value(), inboxId, responseJson, dt(now)),
                "complete receipt inbox");
    }

    @Override
    public Optional<ReceiptRoute> findReceiptRoute(AdenWorkspaceId workspaceId,
                                                    AdenDeliveryId deliveryId) {
        return Optional.ofNullable(mapper.selectDeliveryRoute(workspaceId.value(), deliveryId.value()))
                .map(row -> new ReceiptRoute(row.getTaskId(), row.getStepId()));
    }

    @Override
    public Optional<ReceiptStep> lockReceiptStep(AdenWorkspaceId workspaceId, String stepId) {
        return Optional.ofNullable(mapper.selectReceiptStepForUpdate(workspaceId.value(), stepId))
                .map(this::receiptStep);
    }

    @Override
    public Optional<ReceiptDelivery> lockReceiptDelivery(AdenWorkspaceId workspaceId,
                                                          AdenDeliveryId deliveryId) {
        return Optional.ofNullable(mapper.selectReceiptDeliveryForUpdate(
                workspaceId.value(), deliveryId.value())).map(this::receiptDelivery);
    }

    @Override
    public void applyReceiptDelivery(ReceiptDelivery delivery, String nextState,
                                     long receiptSequence, String outcomeJson,
                                     String errorCode, String errorMessage) {
        one(mapper.applyReceiptDelivery(delivery.workspaceId().value(), delivery.deliveryId().value(),
                delivery.state(), nextState, delivery.version(), receiptSequence, outcomeJson,
                errorCode, errorMessage), "apply receipt delivery");
    }

    @Override
    public void retryAfterReceipt(ReceiptDelivery delivery, String newDeliveryId,
                                  String taskPackageJson, String taskPackageHash,
                                  long receiptSequence, String outcomeJson,
                                  String errorCode, String errorMessage) {
        one(mapper.failStartedForRetry(delivery.workspaceId().value(), delivery.deliveryId().value(),
                delivery.version(), receiptSequence, outcomeJson, errorCode, errorMessage),
                "fail started delivery for retry");
        one(mapper.insertRetryDelivery(delivery.workspaceId().value(), newDeliveryId,
                delivery.taskId(), delivery.stepId(), delivery.attemptNo() + 1,
                taskPackageJson, taskPackageHash), "insert receipt retry delivery");
    }

    @Override
    public void applyReceiptStep(ReceiptStep step, String nextState, String outputJson,
                                 String errorCode, String errorMessage) {
        one(mapper.applyReceiptStep(step.workspaceId().value(), step.stepId(), step.state(), nextState,
                step.version(), outputJson, errorCode, errorMessage), "apply receipt step");
    }

    @Override
    public void decreaseInFlight(AdenRunnerSessionPrincipal principal, int count) {
        one(mapper.decreaseInFlight(principal.workspaceId().value(), principal.sessionId().value(),
                principal.sessionEpoch(), count), "decrease in-flight");
    }

    @Override
    public List<RecoveryRoute> findExpiredRoutes(AdenWorkspaceId workspaceId, int limit) {
        return mapper.selectExpiredRoutes(workspaceId.value(), limit).stream()
                .map(row -> new RecoveryRoute(row.getRunnerId(), row.getOwnerSessionId(),
                        row.getOwnerSessionEpoch(), row.getTaskId(), row.getStepId(),
                        new AdenDeliveryId(row.getDeliveryId()))).toList();
    }

    @Override
    public void requeueExpiredUnstarted(ReceiptDelivery delivery) {
        one(mapper.requeueExpiredUnstarted(delivery.workspaceId().value(),
                delivery.deliveryId().value(), delivery.version()), "requeue expired delivery");
    }

    @Override
    public void expireAsOutcomeUnknown(ReceiptDelivery delivery) {
        one(mapper.expireAsOutcomeUnknown(delivery.workspaceId().value(),
                delivery.deliveryId().value(), delivery.version()), "mark outcome unknown");
    }

    @Override
    public void retryExpiredStarted(ReceiptDelivery delivery, String newDeliveryId,
                                    String taskPackageJson, String taskPackageHash) {
        one(mapper.failExpiredStarted(delivery.workspaceId().value(), delivery.deliveryId().value(),
                delivery.version()), "fail expired started delivery");
        one(mapper.insertRetryDelivery(delivery.workspaceId().value(), newDeliveryId,
                delivery.taskId(), delivery.stepId(), delivery.attemptNo() + 1,
                taskPackageJson, taskPackageHash), "insert retry delivery");
    }

    @Override
    public void updateRecoveryStep(ReceiptStep step, String nextState, String errorCode) {
        one(mapper.updateRecoveryStep(step.workspaceId().value(), step.stepId(), step.version(),
                nextState, errorCode), "update recovery step");
    }

    private ClaimInbox inbox(AdenInboxRow row) {
        return new ClaimInbox(new AdenWorkspaceId(row.getWorkspaceId()), row.getInboxId(),
                new AdenSessionId(row.getProducerId()), new AdenIdempotencyKey(row.getIdempotencyKey()),
                row.getRequestHash(), row.getInboxState(), row.getResponseJson(),
                instant(row.getInProgressUntil()), instant(row.getRetentionUntil()),
                instant(row.getCreatedAt()));
    }

    private SessionCapacity session(AdenRunnerSessionRow row) {
        return new SessionCapacity(new AdenWorkspaceId(row.getWorkspaceId()),
                new AdenSessionId(row.getSessionId()), row.getSessionEpoch(),
                row.getRunnerCurrentSessionEpoch(), row.getSessionStatus(), row.getCapacity(),
                row.getInFlight(), row.getVersion(), instant(row.getExpiresAt()),
                instant(row.getDatabaseNow()));
    }

    private ClaimedDelivery delivery(AdenDeliveryRow row) {
        return new ClaimedDelivery(new AdenWorkspaceId(row.getWorkspaceId()),
                new AdenDeliveryId(row.getDeliveryId()), row.getTaskId(), row.getStepId(),
                row.getAttemptNo(), row.getFenceToken(), instant(row.getLeaseUntil()),
                row.getTaskPackageJson(), row.getTaskPackageHash(),
                new AdenSessionId(row.getOwnerSessionId()), row.getOwnerSessionEpoch());
    }

    private ReceiptStep receiptStep(AdenTaskStepRow row) {
        return new ReceiptStep(new AdenWorkspaceId(row.getWorkspaceId()), row.getTaskId(), row.getStepId(), row.getStepState(),
                row.getAttemptNo(), row.getVersion());
    }

    private ReceiptDelivery receiptDelivery(AdenDeliveryRow row) {
        return new ReceiptDelivery(new AdenWorkspaceId(row.getWorkspaceId()), new AdenDeliveryId(row.getDeliveryId()), row.getTaskId(),
                row.getStepId(), row.getDeliveryState(), row.getAttemptNo(), row.getTaskPackageJson(),
                row.getTaskPackageHash(), row.getOwnerSessionId(),
                row.getOwnerSessionEpoch(), row.getFenceToken(), row.getLatestReceiptSequence(),
                instant(row.getLeaseUntil()), instant(row.getCancelRequestedAt()),
                instant(row.getDatabaseNow()), row.getVersion());
    }



    private static LocalDateTime dt(Instant value) {
        return value == null ? null : AdenUtcDateTimeCodec.toDatabase(value);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : AdenUtcDateTimeCodec.fromDatabase(value);
    }

    private static void one(int count, String operation) {
        if (count != 1) throw new IllegalStateException("Runner " + operation + " 竞争失败");
    }
}
