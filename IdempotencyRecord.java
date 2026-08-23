package com.aiinterviewcoach.domain.platform;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** tenant + principal + operation + key 作用域内的幂等事实。 */
public final class IdempotencyRecord {

    private final ResourceId id;
    private final TenantId tenantId;
    private final String principalRefHash;
    private final String operation;
    private final IdempotencyKey idempotencyKey;
    private final String requestHash;
    private final Instant expiresAt;
    private IdempotencyState state;
    private Map<String, String> resourceReferences;
    private Integer responseStatus;
    private String errorCode;
    private AggregateVersion version;

    private IdempotencyRecord(
            ResourceId id,
            TenantId tenantId,
            String principalRefHash,
            String operation,
            IdempotencyKey idempotencyKey,
            String requestHash,
            Instant expiresAt
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "idempotencyRecordId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.principalRefHash = DomainPreconditions.requireText(principalRefHash, "principalRefHash");
        this.operation = DomainPreconditions.requireText(operation, "operation");
        this.idempotencyKey = DomainPreconditions.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
        this.expiresAt = DomainPreconditions.requireNonNull(expiresAt, "idempotencyExpiresAt");
        this.state = IdempotencyState.PROCESSING;
        this.resourceReferences = Map.of();
        this.version = AggregateVersion.initial();
    }

    public static IdempotencyRecord start(
            ResourceId id,
            TenantId tenantId,
            String principalRefHash,
            String operation,
            IdempotencyKey idempotencyKey,
            String requestHash,
            Instant expiresAt,
            Instant startedAt
    ) {
        DomainPreconditions.require(expiresAt.isAfter(startedAt), DomainErrorCode.INVALID_ARGUMENT,
                "idempotency record must expire after it starts");
        return new IdempotencyRecord(id, tenantId, principalRefHash, operation,
                idempotencyKey, requestHash, expiresAt);
    }

    /** 从 tenant/principal/operation scoped persistence 重建。 */
    public static IdempotencyRecord rehydrate(
            ResourceId id,
            TenantId tenantId,
            String principalRefHash,
            String operation,
            IdempotencyKey idempotencyKey,
            String requestHash,
            Instant expiresAt,
            IdempotencyState state,
            Map<String, String> resourceReferences,
            Integer responseStatus,
            String errorCode,
            AggregateVersion version
    ) {
        IdempotencyRecord record = new IdempotencyRecord(id, tenantId, principalRefHash, operation,
                idempotencyKey, requestHash, expiresAt);
        record.state = DomainPreconditions.requireNonNull(state, "idempotencyState");
        record.resourceReferences = immutableReferences(resourceReferences);
        record.responseStatus = responseStatus;
        record.errorCode = errorCode;
        record.version = DomainPreconditions.requireNonNull(version, "idempotencyVersion");
        record.assertConsistentState();
        return record;
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String principalRefHash() { return principalRefHash; }
    public String operation() { return operation; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public String requestHash() { return requestHash; }
    public Instant expiresAt() { return expiresAt; }
    public IdempotencyState state() { return state; }
    public Map<String, String> resourceReferences() { return resourceReferences; }
    public Optional<Integer> responseStatus() { return Optional.ofNullable(responseStatus); }
    public Optional<String> errorCode() { return Optional.ofNullable(errorCode); }
    public AggregateVersion version() { return version; }

    public void assertSameRequest(String requestHash) {
        DomainPreconditions.require(this.requestHash.equals(requestHash), DomainErrorCode.IDEMPOTENCY_CONFLICT,
                "idempotency key was already used with another request");
    }

    public void succeed(
            Map<String, String> resourceReferences,
            int responseStatus,
            AggregateVersion expectedVersion
    ) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(state == IdempotencyState.PROCESSING,
                DomainErrorCode.INVALID_STATE, "idempotency record is already finalized");
        DomainPreconditions.require(responseStatus >= 200 && responseStatus < 400,
                DomainErrorCode.INVALID_ARGUMENT, "success response status is invalid");
        this.resourceReferences = immutableReferences(resourceReferences);
        this.responseStatus = responseStatus;
        state = IdempotencyState.SUCCEEDED;
        version = version.next();
    }

    public void failReplayable(
            String errorCode,
            int responseStatus,
            Map<String, String> resourceReferences,
            AggregateVersion expectedVersion
    ) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(state == IdempotencyState.PROCESSING,
                DomainErrorCode.INVALID_STATE, "idempotency record is already finalized");
        String checkedErrorCode = DomainPreconditions.requireText(errorCode, "errorCode");
        DomainPreconditions.require(responseStatus >= 400 && responseStatus < 600,
                DomainErrorCode.INVALID_ARGUMENT, "failure response status is invalid");
        Map<String, String> checkedReferences = immutableReferences(resourceReferences);
        this.errorCode = checkedErrorCode;
        this.responseStatus = responseStatus;
        this.resourceReferences = checkedReferences;
        state = IdempotencyState.FAILED_REPLAYABLE;
        version = version.next();
    }

    public void expire(Instant now, AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
        DomainPreconditions.require(!now.isBefore(expiresAt), DomainErrorCode.INVALID_STATE,
                "idempotency record cannot expire before expiresAt");
        DomainPreconditions.require(state != IdempotencyState.SUCCEEDED,
                DomainErrorCode.POLICY_DENIED, "successful high-risk idempotency fact cannot be expired generically");
        state = IdempotencyState.EXPIRED;
        version = version.next();
    }

    public boolean isReplayable() {
        return state == IdempotencyState.SUCCEEDED || state == IdempotencyState.FAILED_REPLAYABLE;
    }

    private static Map<String, String> immutableReferences(Map<String, String> references) {
        Map<String, String> result = Map.copyOf(new LinkedHashMap<>(references == null ? Map.of() : references));
        result.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "idempotency resource reference key");
            DomainPreconditions.requireText(value, "idempotency resource reference value");
        });
        return result;
    }

    private void assertConsistentState() {
        if (state == IdempotencyState.SUCCEEDED) {
            DomainPreconditions.require(responseStatus != null && responseStatus >= 200 && responseStatus < 400
                            && errorCode == null,
                    DomainErrorCode.INVALID_STATE, "successful idempotency record is inconsistent");
        }
        if (state == IdempotencyState.FAILED_REPLAYABLE) {
            DomainPreconditions.require(responseStatus != null && responseStatus >= 400 && responseStatus < 600
                            && errorCode != null,
                    DomainErrorCode.INVALID_STATE, "failed idempotency record is inconsistent");
        }
        if (state == IdempotencyState.PROCESSING) {
            DomainPreconditions.require(responseStatus == null && errorCode == null,
                    DomainErrorCode.INVALID_STATE, "processing idempotency record cannot contain a response");
        }
    }
}
