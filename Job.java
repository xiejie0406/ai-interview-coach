package com.aiinterviewcoach.domain.platform;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lease Job 的确定性状态机。payload 只能是最小资源引用；真正外部调用在事务外执行。
 */
public final class Job extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final String jobType;
    private final ResourceId businessOperationId;
    private final Map<String, String> payloadReferences;
    private final int maxAttempts;
    private JobState state;
    private Instant availableAt;
    private String leaseOwner;
    private Instant leaseExpiresAt;
    private Instant heartbeatAt;
    private int attemptCount;
    private String lastErrorCode;
    private AggregateVersion version;

    private Job(
            ResourceId id,
            TenantId tenantId,
            String jobType,
            ResourceId businessOperationId,
            Map<String, String> payloadReferences,
            int maxAttempts,
            JobState state,
            Instant availableAt,
            int attemptCount,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "jobId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.jobType = DomainPreconditions.requireText(jobType, "jobType");
        this.businessOperationId = DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
        this.payloadReferences = Map.copyOf(new LinkedHashMap<>(
                payloadReferences == null ? Map.of() : payloadReferences));
        this.payloadReferences.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "job payload reference key");
            DomainPreconditions.requireText(value, "job payload reference value");
        });
        DomainPreconditions.require(maxAttempts > 0, DomainErrorCode.INVALID_ARGUMENT,
                "job max attempts must be positive");
        this.maxAttempts = maxAttempts;
        this.state = DomainPreconditions.requireNonNull(state, "jobState");
        this.availableAt = DomainPreconditions.requireNonNull(availableAt, "jobAvailableAt");
        DomainPreconditions.require(attemptCount >= 0 && attemptCount <= maxAttempts,
                DomainErrorCode.INVALID_ARGUMENT, "job attempt count is invalid");
        this.attemptCount = attemptCount;
        this.version = DomainPreconditions.requireNonNull(version, "jobVersion");
    }

    public static Job schedule(
            ResourceId id,
            TenantId tenantId,
            String jobType,
            ResourceId businessOperationId,
            Map<String, String> payloadReferences,
            int maxAttempts,
            Instant availableAt,
            EventContext context
    ) {
        Job job = new Job(id, tenantId, jobType, businessOperationId, payloadReferences, maxAttempts,
                JobState.PENDING, availableAt, 0, AggregateVersion.initial());
        job.recordEvent("platform.job.scheduled", tenantId, id, job.version, context,
                Map.of("jobType", jobType, "businessOperationId", businessOperationId.value().toString()));
        return job;
    }

    /** 从 tenant-scoped persistence 重建；不产生事件。 */
    public static Job rehydrate(
            ResourceId id,
            TenantId tenantId,
            String jobType,
            ResourceId businessOperationId,
            Map<String, String> payloadReferences,
            int maxAttempts,
            JobState state,
            Instant availableAt,
            String leaseOwner,
            Instant leaseExpiresAt,
            Instant heartbeatAt,
            int attemptCount,
            String lastErrorCode,
            AggregateVersion version
    ) {
        Job job = new Job(id, tenantId, jobType, businessOperationId, payloadReferences, maxAttempts,
                state, availableAt, attemptCount, version);
        job.leaseOwner = leaseOwner;
        job.leaseExpiresAt = leaseExpiresAt;
        job.heartbeatAt = heartbeatAt;
        job.lastErrorCode = lastErrorCode;
        job.assertConsistentState();
        return job;
    }

    public ResourceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String jobType() {
        return jobType;
    }

    public ResourceId businessOperationId() {
        return businessOperationId;
    }

    public Map<String, String> payloadReferences() {
        return payloadReferences;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public JobState state() {
        return state;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Optional<String> leaseOwner() {
        return Optional.ofNullable(leaseOwner);
    }

    public Optional<Instant> leaseExpiresAt() {
        return Optional.ofNullable(leaseExpiresAt);
    }

    public Optional<Instant> heartbeatAt() {
        return Optional.ofNullable(heartbeatAt);
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Optional<String> lastErrorCode() {
        return Optional.ofNullable(lastErrorCode);
    }

    public AggregateVersion version() {
        return version;
    }

    public int claim(
            String workerId,
            Instant now,
            Duration leaseDuration,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == JobState.PENDING, DomainErrorCode.INVALID_STATE,
                "only a pending job can be claimed");
        DomainPreconditions.require(!now.isBefore(availableAt), DomainErrorCode.INVALID_STATE,
                "job is not available yet");
        DomainPreconditions.require(!leaseDuration.isNegative() && !leaseDuration.isZero(),
                DomainErrorCode.INVALID_ARGUMENT, "job lease duration must be positive");
        DomainPreconditions.require(attemptCount < maxAttempts, DomainErrorCode.RETRY_NOT_ALLOWED,
                "job attempt budget is exhausted");
        leaseOwner = DomainPreconditions.requireText(workerId, "workerId");
        leaseExpiresAt = now.plus(leaseDuration);
        heartbeatAt = now;
        attemptCount++;
        state = JobState.RUNNING;
        bump("platform.job.claimed", context,
                Map.of("attemptNo", Integer.toString(attemptCount), "workerId", leaseOwner));
        return attemptCount;
    }

    public void heartbeat(
            String workerId,
            int attemptNo,
            Instant now,
            Duration leaseDuration,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        requireLeaseOwner(workerId, attemptNo, now);
        DomainPreconditions.require(!leaseDuration.isNegative() && !leaseDuration.isZero(),
                DomainErrorCode.INVALID_ARGUMENT, "job lease duration must be positive");
        heartbeatAt = now;
        leaseExpiresAt = now.plus(leaseDuration);
        version = version.next();
    }

    public void succeed(
            String workerId,
            int attemptNo,
            Instant now,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        requireLeaseOwner(workerId, attemptNo, now);
        DomainPreconditions.require(state == JobState.RUNNING || state == JobState.CANCEL_REQUESTED,
                DomainErrorCode.INVALID_STATE, "job cannot succeed from current state");
        state = JobState.SUCCEEDED;
        clearLease();
        bump("platform.job.succeeded", context, Map.of("attemptNo", Integer.toString(attemptCount)));
    }

    public void fail(
            String workerId,
            int attemptNo,
            Instant now,
            JobFailure failure,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        requireLeaseOwner(workerId, attemptNo, now);
        DomainPreconditions.require(state == JobState.RUNNING || state == JobState.CANCEL_REQUESTED,
                DomainErrorCode.INVALID_STATE, "job cannot fail from current state");
        DomainPreconditions.requireNonNull(failure, "jobFailure");
        lastErrorCode = failure.errorCode();
        boolean retryable = state != JobState.CANCEL_REQUESTED
                && failure.retryDisposition().permitsAutomaticRetry()
                && attemptCount < maxAttempts;
        if (retryable) {
            state = JobState.FAILED_RETRYABLE;
            availableAt = failure.nextAttemptAt().orElseThrow();
        } else {
            state = JobState.FAILED_FINAL;
        }
        clearLease();
        bump(retryable ? "platform.job.failed_retryable" : "platform.job.failed_final", context,
                Map.of("attemptNo", Integer.toString(attemptCount), "errorCode", lastErrorCode));
    }

    public void makePending(Instant now, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == JobState.FAILED_RETRYABLE, DomainErrorCode.INVALID_STATE,
                "only retryable failure can become pending");
        DomainPreconditions.require(!now.isBefore(availableAt), DomainErrorCode.INVALID_STATE,
                "job retry time has not arrived");
        state = JobState.PENDING;
        bump("platform.job.retry_scheduled", context, Map.of("attemptNo", Integer.toString(attemptCount + 1)));
    }

    public void requestCancel(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        if (state == JobState.PENDING || state == JobState.FAILED_RETRYABLE) {
            state = JobState.CANCELLED;
            clearLease();
            bump("platform.job.cancelled", context, Map.of());
            return;
        }
        DomainPreconditions.require(state == JobState.RUNNING, DomainErrorCode.JOB_NOT_CANCELLABLE,
                "job cannot accept cancellation in current state");
        state = JobState.CANCEL_REQUESTED;
        bump("platform.job.cancel_requested", context, Map.of("attemptNo", Integer.toString(attemptCount)));
    }

    public void confirmCancelled(
            String workerId,
            int attemptNo,
            Instant now,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        requireLeaseOwner(workerId, attemptNo, now);
        DomainPreconditions.require(state == JobState.CANCEL_REQUESTED, DomainErrorCode.INVALID_STATE,
                "job cancellation has not been requested");
        state = JobState.CANCELLED;
        clearLease();
        bump("platform.job.cancelled", context, Map.of("attemptNo", Integer.toString(attemptCount)));
    }

    public void reclaimExpiredLease(Instant now, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == JobState.RUNNING || state == JobState.CANCEL_REQUESTED,
                DomainErrorCode.INVALID_STATE, "job does not have a reclaimable execution lease");
        DomainPreconditions.require(leaseExpiresAt != null && !now.isBefore(leaseExpiresAt),
                DomainErrorCode.JOB_LEASE_NOT_HELD, "job lease has not expired");
        DomainPreconditions.require(state != JobState.CANCEL_REQUESTED,
                DomainErrorCode.RETRY_NOT_ALLOWED,
                "expired cancellation lease requires provider readback or compensation evidence");
        state = attemptCount < maxAttempts ? JobState.PENDING : JobState.FAILED_FINAL;
        if (state == JobState.FAILED_FINAL) {
            lastErrorCode = "LEASE_EXPIRED_ATTEMPTS_EXHAUSTED";
        }
        availableAt = now;
        clearLease();
        bump(state == JobState.PENDING ? "platform.job.lease_expired" : "platform.job.failed_final", context,
                Map.of("attemptNo", Integer.toString(attemptCount)));
    }

    /**
     * CANCEL_REQUESTED lease 到期后，仅在应用层已持久化“无 effect/补偿完成”回执时调用。
     */
    public void reconcileExpiredCancellationAsCancelled(
            ResourceId reconciliationReceiptId,
            Instant now,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        requireExpiredCancellationForReconciliation(now, expectedVersion);
        DomainPreconditions.requireNonNull(reconciliationReceiptId, "reconciliationReceiptId");
        DomainPreconditions.requireNonNull(context, "eventContext");
        state = JobState.CANCELLED;
        clearLease();
        bump("platform.job.cancellation_reconciled", context,
                Map.of("attemptNo", Integer.toString(attemptCount),
                        "reconciliationReceiptId", reconciliationReceiptId.value()));
    }

    /**
     * CANCEL_REQUESTED lease 到期后，仅在应用层已持久化“不可逆 effect 已成功”回读回执时调用。
     */
    public void reconcileExpiredCancellationAsSucceeded(
            ResourceId reconciliationReceiptId,
            Instant now,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        requireExpiredCancellationForReconciliation(now, expectedVersion);
        DomainPreconditions.requireNonNull(reconciliationReceiptId, "reconciliationReceiptId");
        DomainPreconditions.requireNonNull(context, "eventContext");
        state = JobState.SUCCEEDED;
        clearLease();
        bump("platform.job.success_reconciled", context,
                Map.of("attemptNo", Integer.toString(attemptCount),
                        "reconciliationReceiptId", reconciliationReceiptId.value()));
    }

    private void requireExpiredCancellationForReconciliation(
            Instant now,
            AggregateVersion expectedVersion
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == JobState.CANCEL_REQUESTED,
                DomainErrorCode.INVALID_STATE, "job cancellation is not awaiting reconciliation");
        DomainPreconditions.requireNonNull(now, "reconciledAt");
        DomainPreconditions.require(leaseExpiresAt != null && !now.isBefore(leaseExpiresAt),
                DomainErrorCode.RETRY_NOT_ALLOWED,
                "active cancellation lease must be resolved by its current worker");
    }

    /** 业务 effect 落库前必须在同一事务中调用，不能只相信 Worker 携带的上下文。 */
    public void requireExecutionLease(
            String workerId,
            int attemptNo,
            Instant now,
            AggregateVersion expectedVersion
    ) {
        expectedVersion(expectedVersion);
        requireLeaseOwner(workerId, attemptNo, now);
    }

    private void requireLeaseOwner(String workerId, int attemptNo, Instant now) {
        DomainPreconditions.require(state == JobState.RUNNING || state == JobState.CANCEL_REQUESTED,
                DomainErrorCode.JOB_LEASE_NOT_HELD, "job is not running under a lease");
        DomainPreconditions.require(leaseOwner != null && leaseOwner.equals(workerId),
                DomainErrorCode.JOB_LEASE_NOT_HELD, "worker does not own job lease");
        DomainPreconditions.require(this.attemptCount == attemptNo,
                DomainErrorCode.JOB_LEASE_NOT_HELD, "worker attempt is no longer current");
        DomainPreconditions.require(leaseExpiresAt != null && now.isBefore(leaseExpiresAt),
                DomainErrorCode.JOB_LEASE_NOT_HELD, "job lease has expired");
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
        heartbeatAt = null;
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private void assertConsistentState() {
        boolean leaseRequired = state == JobState.RUNNING || state == JobState.CANCEL_REQUESTED;
        DomainPreconditions.require(leaseRequired == (leaseOwner != null
                        && leaseExpiresAt != null && heartbeatAt != null),
                DomainErrorCode.INVALID_STATE, "job lease fields are inconsistent with state");
        if (leaseRequired || state == JobState.SUCCEEDED
                || state == JobState.FAILED_RETRYABLE || state == JobState.FAILED_FINAL) {
            DomainPreconditions.require(attemptCount > 0, DomainErrorCode.INVALID_STATE,
                    "executed job state requires at least one attempt");
        }
        if (state == JobState.FAILED_RETRYABLE || state == JobState.FAILED_FINAL) {
            DomainPreconditions.requireNonNull(lastErrorCode, "lastErrorCode");
        }
    }
}
