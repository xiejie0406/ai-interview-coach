package com.ruoyi.aden.domain.runner;

import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskStepId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/** 带 session epoch、lease 与单调 fence 的执行投递事实。 */
public record AdenRunnerDelivery(
        AdenWorkspaceId workspaceId,
        AdenDeliveryId id,
        AdenTaskId taskId,
        AdenTaskStepId stepId,
        AdenDeliveryState state,
        int attemptNo,
        AdenCapabilityCode requiredCapability,
        int priority,
        String taskPackageJson,
        String taskPackageHash,
        AdenFencingToken fencingToken,
        AdenLease lease,
        long latestReceiptSequence,
        long heartbeatSequence,
        Instant cancelRequestedAt,
        long version) {
    public AdenRunnerDelivery {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(state, "state");
        if (attemptNo < 1 || attemptNo > 100 || priority < 0 || priority > 1000
                || latestReceiptSequence < 0 || heartbeatSequence < 0 || version < 0) {
            throw new IllegalArgumentException("Delivery 计数非法");
        }
        Objects.requireNonNull(requiredCapability, "requiredCapability");
        if (requiredCapability != AdenCapabilityCode.CORE) {
            throw new IllegalArgumentException("当前合成 Delivery 只允许 CORE");
        }
        Objects.requireNonNull(taskPackageJson, "taskPackageJson");
        if (taskPackageHash == null || !taskPackageHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("task package hash 非法");
        }
        Objects.requireNonNull(fencingToken, "fencingToken");
        if (state == AdenDeliveryState.READY && lease != null) {
            throw new IllegalArgumentException("READY 必须无 lease");
        }
        if ((state == AdenDeliveryState.LEASED || state == AdenDeliveryState.RUNNING) && lease == null) {
            throw new IllegalArgumentException("活动 Delivery 必须有 lease");
        }
    }

    public AdenRunnerDelivery claim(AdenRunnerSession session, Instant leaseUntil) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (state != AdenDeliveryState.READY || session.remainingCapacity() < 1
                || !session.workspaceId().equals(workspaceId)) {
            throw new IllegalStateException("Delivery 当前不可领取");
        }
        AdenFencingToken nextFence = fencingToken.next();
        return new AdenRunnerDelivery(workspaceId, id, taskId, stepId, AdenDeliveryState.LEASED,
                attemptNo, requiredCapability, priority, taskPackageJson, taskPackageHash,
                nextFence, new AdenLease(session.id(), session.epoch(), nextFence, leaseUntil),
                latestReceiptSequence, heartbeatSequence, cancelRequestedAt, version + 1);
    }

    public AdenRunnerDelivery apply(AdenRunnerReceipt receipt, Instant now) {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(now, "now");
        if (!id.equals(receipt.deliveryId()) || lease == null
                || !lease.validFor(receipt.sessionId(), receipt.sessionEpoch(),
                receipt.fencingToken(), now)) throw new IllegalStateException("ADEN_RECEIPT_STALE");
        if (receipt.receiptSequence() != latestReceiptSequence + 1) {
            throw new IllegalStateException("ADEN_RECEIPT_OUT_OF_ORDER");
        }
        AdenDeliveryState next = switch (receipt.type()) {
            case STARTED -> requireState(AdenDeliveryState.LEASED, AdenDeliveryState.RUNNING);
            case PROGRESS -> requireState(AdenDeliveryState.RUNNING, AdenDeliveryState.RUNNING);
            case COMPLETED -> requireState(AdenDeliveryState.RUNNING, AdenDeliveryState.COMPLETED);
            case FAILED_RETRYABLE -> requireActive(AdenDeliveryState.FAILED_RETRYABLE);
            case FAILED_FINAL -> requireActive(AdenDeliveryState.FAILED_FINAL);
            case CANCELED_SAFE_POINT -> {
                if (cancelRequestedAt == null) throw new IllegalStateException("未请求取消");
                yield requireActive(AdenDeliveryState.CANCELED);
            }
            case OUTCOME_UNKNOWN -> {
                if (cancelRequestedAt == null) throw new IllegalStateException("未请求取消");
                yield requireActive(AdenDeliveryState.OUTCOME_UNKNOWN);
            }
        };
        return new AdenRunnerDelivery(workspaceId, id, taskId, stepId, next, attemptNo,
                requiredCapability, priority, taskPackageJson, taskPackageHash, fencingToken,
                lease, receipt.receiptSequence(), heartbeatSequence, cancelRequestedAt, version + 1);
    }

    public AdenRunnerDelivery requestCancel(Instant requestedAt) {
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (state != AdenDeliveryState.LEASED && state != AdenDeliveryState.RUNNING) {
            throw new IllegalStateException("只有活动 Delivery 可请求取消");
        }
        if (cancelRequestedAt != null) return this;
        return new AdenRunnerDelivery(workspaceId, id, taskId, stepId, state, attemptNo,
                requiredCapability, priority, taskPackageJson, taskPackageHash, fencingToken,
                lease, latestReceiptSequence, heartbeatSequence, requestedAt, version + 1);
    }

    private AdenDeliveryState requireState(AdenDeliveryState expected, AdenDeliveryState next) {
        if (state != expected) throw new IllegalStateException("Delivery 状态不允许该回执");
        return next;
    }

    private AdenDeliveryState requireActive(AdenDeliveryState next) {
        if (state != AdenDeliveryState.LEASED && state != AdenDeliveryState.RUNNING) {
            throw new IllegalStateException("Delivery 状态不允许该回执");
        }
        return next;
    }
}
