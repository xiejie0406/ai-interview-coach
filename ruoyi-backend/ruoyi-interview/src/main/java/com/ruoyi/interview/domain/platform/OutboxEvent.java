package com.ruoyi.interview.domain.platform;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 事务 Outbox 事实；payloadReferences 禁止保存用户正文。 */
public final class OutboxEvent {

    private final ResourceId id;
    private final TenantId tenantId;
    private final String aggregateType;
    private final ResourceId aggregateId;
    private final AggregateVersion aggregateVersion;
    private final String eventType;
    private final int schemaVersion;
    private final CorrelationId correlationId;
    private final Map<String, String> payloadReferences;
    private OutboxState state;
    private Instant availableAt;
    private String claimedBy;
    private Instant claimExpiresAt;
    private Instant publishedAt;
    private int attemptCount;
    private String lastErrorCode;
    private AggregateVersion version;

    public OutboxEvent(
            ResourceId id,
            TenantId tenantId,
            String aggregateType,
            ResourceId aggregateId,
            AggregateVersion aggregateVersion,
            String eventType,
            int schemaVersion,
            CorrelationId correlationId,
            Map<String, String> payloadReferences,
            Instant availableAt
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "outboxEventId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.aggregateType = DomainPreconditions.requireText(aggregateType, "aggregateType");
        this.aggregateId = DomainPreconditions.requireNonNull(aggregateId, "aggregateId");
        this.aggregateVersion = DomainPreconditions.requireNonNull(aggregateVersion, "aggregateVersion");
        this.eventType = DomainPreconditions.requireText(eventType, "eventType");
        DomainPreconditions.require(schemaVersion > 0, DomainErrorCode.INVALID_ARGUMENT,
                "event schema version must be positive");
        this.schemaVersion = schemaVersion;
        this.correlationId = DomainPreconditions.requireNonNull(correlationId, "correlationId");
        this.payloadReferences = Map.copyOf(new LinkedHashMap<>(
                payloadReferences == null ? Map.of() : payloadReferences));
        this.payloadReferences.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "outbox payload reference key");
            DomainPreconditions.requireText(value, "outbox payload reference value");
        });
        this.state = OutboxState.PENDING;
        this.availableAt = DomainPreconditions.requireNonNull(availableAt, "outboxAvailableAt");
        this.version = AggregateVersion.initial();
    }

    /** 从持久化事实重建；不执行状态迁移。 */
    public static OutboxEvent rehydrate(
            ResourceId id,
            TenantId tenantId,
            String aggregateType,
            ResourceId aggregateId,
            AggregateVersion sourceAggregateVersion,
            String eventType,
            int schemaVersion,
            CorrelationId correlationId,
            Map<String, String> payloadReferences,
            OutboxState state,
            Instant availableAt,
            String claimedBy,
            Instant claimExpiresAt,
            Instant publishedAt,
            int attemptCount,
            String lastErrorCode,
            AggregateVersion version
    ) {
        OutboxEvent event = new OutboxEvent(id, tenantId, aggregateType, aggregateId,
                sourceAggregateVersion, eventType, schemaVersion, correlationId, payloadReferences, availableAt);
        event.state = DomainPreconditions.requireNonNull(state, "outboxState");
        event.claimedBy = claimedBy;
        event.claimExpiresAt = claimExpiresAt;
        event.publishedAt = publishedAt;
        DomainPreconditions.require(attemptCount >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "outbox attempt count must not be negative");
        event.attemptCount = attemptCount;
        event.lastErrorCode = lastErrorCode;
        event.version = DomainPreconditions.requireNonNull(version, "outboxVersion");
        event.assertConsistentState();
        return event;
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String aggregateType() { return aggregateType; }
    public ResourceId aggregateId() { return aggregateId; }
    public AggregateVersion sourceAggregateVersion() { return aggregateVersion; }
    public String eventType() { return eventType; }
    public int schemaVersion() { return schemaVersion; }
    public CorrelationId correlationId() { return correlationId; }
    public Map<String, String> payloadReferences() { return payloadReferences; }
    public OutboxState state() { return state; }
    public Instant availableAt() { return availableAt; }
    public Optional<String> claimedBy() { return Optional.ofNullable(claimedBy); }
    public Optional<Instant> claimExpiresAt() { return Optional.ofNullable(claimExpiresAt); }
    public Optional<Instant> publishedAt() { return Optional.ofNullable(publishedAt); }
    public int attemptCount() { return attemptCount; }
    public Optional<String> lastErrorCode() { return Optional.ofNullable(lastErrorCode); }
    public AggregateVersion version() { return version; }

    public void claim(String publisherId, Instant now, Duration lease, AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(state == OutboxState.PENDING || state == OutboxState.FAILED_RETRYABLE,
                DomainErrorCode.INVALID_STATE, "outbox event cannot be claimed from current state");
        DomainPreconditions.require(!now.isBefore(availableAt), DomainErrorCode.INVALID_STATE,
                "outbox event is not available yet");
        DomainPreconditions.require(!lease.isNegative() && !lease.isZero(), DomainErrorCode.INVALID_ARGUMENT,
                "outbox lease must be positive");
        claimedBy = DomainPreconditions.requireText(publisherId, "publisherId");
        claimExpiresAt = now.plus(lease);
        attemptCount++;
        state = OutboxState.CLAIMED;
        version = version.next();
    }

    public void publish(String publisherId, Instant now, AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
        requireClaimOwner(publisherId, now);
        state = OutboxState.PUBLISHED;
        publishedAt = now;
        clearClaim();
        version = version.next();
    }

    public void fail(
            String publisherId,
            Instant now,
            String errorCode,
            RetryDisposition disposition,
            Instant retryAt,
            AggregateVersion expectedVersion
    ) {
        version.requireMatches(expectedVersion);
        requireClaimOwner(publisherId, now);
        String checkedErrorCode = DomainPreconditions.requireText(errorCode, "outboxErrorCode");
        RetryDisposition checkedDisposition = DomainPreconditions.requireNonNull(
                disposition, "retryDisposition");
        if (checkedDisposition.permitsAutomaticRetry()) {
            Instant checkedRetryAt = DomainPreconditions.requireNonNull(retryAt, "retryAt");
            DomainPreconditions.require(checkedRetryAt.isAfter(now), DomainErrorCode.INVALID_ARGUMENT,
                    "outbox retry time must be in the future");
        }
        lastErrorCode = checkedErrorCode;
        if (checkedDisposition.permitsAutomaticRetry()) {
            state = OutboxState.FAILED_RETRYABLE;
            availableAt = retryAt;
        } else {
            state = OutboxState.FAILED_FINAL;
        }
        clearClaim();
        version = version.next();
    }

    public void reclaimExpiredClaim(Instant now, AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(state == OutboxState.CLAIMED, DomainErrorCode.INVALID_STATE,
                "outbox event is not claimed");
        DomainPreconditions.require(claimExpiresAt != null && !now.isBefore(claimExpiresAt),
                DomainErrorCode.JOB_LEASE_NOT_HELD, "outbox claim has not expired");
        state = OutboxState.PENDING;
        availableAt = now;
        clearClaim();
        version = version.next();
    }

    private void requireClaimOwner(String publisherId, Instant now) {
        DomainPreconditions.require(state == OutboxState.CLAIMED, DomainErrorCode.JOB_LEASE_NOT_HELD,
                "outbox event is not claimed");
        DomainPreconditions.require(claimedBy != null && claimedBy.equals(publisherId),
                DomainErrorCode.JOB_LEASE_NOT_HELD, "publisher does not own outbox claim");
        DomainPreconditions.require(claimExpiresAt != null && now.isBefore(claimExpiresAt),
                DomainErrorCode.JOB_LEASE_NOT_HELD, "outbox claim has expired");
    }

    private void clearClaim() {
        claimedBy = null;
        claimExpiresAt = null;
    }

    private void assertConsistentState() {
        boolean claimed = state == OutboxState.CLAIMED;
        DomainPreconditions.require(claimed == (claimedBy != null && claimExpiresAt != null),
                DomainErrorCode.INVALID_STATE, "outbox claim fields are inconsistent with state");
        DomainPreconditions.require((state == OutboxState.PUBLISHED) == (publishedAt != null),
                DomainErrorCode.INVALID_STATE, "outbox publishedAt is inconsistent with state");
        if (state == OutboxState.FAILED_RETRYABLE || state == OutboxState.FAILED_FINAL) {
            DomainPreconditions.requireNonNull(lastErrorCode, "outboxLastErrorCode");
        }
    }
}
