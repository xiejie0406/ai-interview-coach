package com.ruoyi.interview.domain.billing;

import com.ruoyi.interview.domain.platform.AggregateRoot;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.IdempotencyKey;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 一次业务 operation 的用量预留；终结只能是 SETTLED、RELEASED 或 EXPIRED 之一。
 */
public final class UsageReservation extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final ResourceId entitlementId;
    private final ResourceId businessOperationId;
    private final IdempotencyKey idempotencyKey;
    private final UsageQuantity reservedQuantity;
    private final Instant expiresAt;
    private UsageReservationState state;
    private Optional<UsageQuantity> settledQuantity;
    private Optional<String> releaseReasonCode;
    private AggregateVersion version;

    private UsageReservation(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId entitlementId,
            ResourceId businessOperationId,
            IdempotencyKey idempotencyKey,
            UsageQuantity reservedQuantity,
            Instant expiresAt,
            UsageReservationState state,
            Optional<UsageQuantity> settledQuantity,
            Optional<String> releaseReasonCode,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "usageReservationId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.entitlementId = DomainPreconditions.requireNonNull(entitlementId, "entitlementId");
        this.businessOperationId = DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
        this.idempotencyKey = DomainPreconditions.requireNonNull(idempotencyKey, "idempotencyKey");
        this.reservedQuantity = DomainPreconditions.requireNonNull(reservedQuantity, "reservedQuantity");
        DomainPreconditions.require(!reservedQuantity.isZero(), DomainErrorCode.INVALID_ARGUMENT,
                "reserved quantity must be positive");
        this.expiresAt = DomainPreconditions.requireNonNull(expiresAt, "reservationExpiresAt");
        this.state = DomainPreconditions.requireNonNull(state, "reservationState");
        this.settledQuantity = settledQuantity == null ? Optional.empty() : settledQuantity;
        this.releaseReasonCode = releaseReasonCode == null ? Optional.empty() : releaseReasonCode;
        this.releaseReasonCode.ifPresent(code -> DomainPreconditions.require(
                code.matches("[A-Z][A-Z0-9_]{0,63}"), DomainErrorCode.INVALID_ARGUMENT,
                "release reason code is invalid"));
        this.version = DomainPreconditions.requireNonNull(version, "reservationVersion");
        if (state == UsageReservationState.SETTLED) {
            DomainPreconditions.require(this.settledQuantity.isPresent() && this.releaseReasonCode.isEmpty(),
                    DomainErrorCode.INVALID_STATE, "settled reservation requires only settled quantity");
            DomainPreconditions.require(reservedQuantity.unit().equals(this.settledQuantity.orElseThrow().unit())
                            && !this.settledQuantity.orElseThrow().isGreaterThan(reservedQuantity),
                    DomainErrorCode.USAGE_EXCEEDED, "settled quantity is inconsistent with reservation");
        } else if (state == UsageReservationState.RELEASED) {
            DomainPreconditions.require(this.releaseReasonCode.isPresent() && this.settledQuantity.isEmpty(),
                    DomainErrorCode.INVALID_STATE, "released reservation requires only a release reason code");
        } else {
            DomainPreconditions.require(this.releaseReasonCode.isEmpty() && this.settledQuantity.isEmpty(),
                    DomainErrorCode.INVALID_STATE, "reservation state contains unexpected finalization data");
        }
    }

    public static UsageReservation reserve(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId entitlementId,
            ResourceId businessOperationId,
            IdempotencyKey idempotencyKey,
            UsageQuantity reservedQuantity,
            Instant expiresAt,
            EventContext context
    ) {
        DomainPreconditions.require(expiresAt.isAfter(context.occurredAt()), DomainErrorCode.RESERVATION_EXPIRED,
                "reservation must expire in the future");
        UsageReservation reservation = new UsageReservation(id, tenantId, userId, entitlementId,
                businessOperationId, idempotencyKey, reservedQuantity, expiresAt,
                UsageReservationState.RESERVED, Optional.empty(), Optional.empty(), AggregateVersion.initial());
        reservation.recordEvent("billing.reservation.created", tenantId, id, reservation.version, context,
                Map.of("businessOperationId", businessOperationId.value().toString(),
                        "unit", reservedQuantity.unit()));
        return reservation;
    }

    /** 从 tenant-scoped persistence 重建；不产生事件。 */
    public static UsageReservation rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId entitlementId,
            ResourceId businessOperationId,
            IdempotencyKey idempotencyKey,
            UsageQuantity reservedQuantity,
            Instant expiresAt,
            UsageReservationState state,
            Optional<UsageQuantity> settledQuantity,
            Optional<String> releaseReasonCode,
            AggregateVersion version
    ) {
        return new UsageReservation(id, tenantId, userId, entitlementId, businessOperationId,
                idempotencyKey, reservedQuantity, expiresAt, state, settledQuantity, releaseReasonCode, version);
    }

    public ResourceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public ResourceId entitlementId() {
        return entitlementId;
    }

    public ResourceId businessOperationId() {
        return businessOperationId;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public UsageQuantity reservedQuantity() {
        return reservedQuantity;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UsageReservationState state() {
        return state;
    }

    public Optional<UsageQuantity> settledQuantity() {
        return settledQuantity;
    }

    public Optional<String> releaseReasonCode() {
        return releaseReasonCode;
    }

    public AggregateVersion version() {
        return version;
    }

    public boolean isFinal() {
        return state != UsageReservationState.RESERVED;
    }

    public void requireUsable(Instant at) {
        DomainPreconditions.require(state == UsageReservationState.RESERVED,
                DomainErrorCode.RESERVATION_ALREADY_FINALIZED, "reservation is already finalized");
        DomainPreconditions.require(at.isBefore(expiresAt), DomainErrorCode.RESERVATION_EXPIRED,
                "reservation has expired");
    }

    public UsageSettlement settle(
            ResourceId settlementId,
            String settlementRuleVersion,
            UsageQuantity actualUsage,
            AggregateVersion expectedVersion,
            Instant at,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.requireNonNull(settlementId, "settlementId");
        settlementRuleVersion = DomainPreconditions.requireText(
                settlementRuleVersion, "settlementRuleVersion");
        DomainPreconditions.requireNonNull(actualUsage, "actualUsage");
        DomainPreconditions.requireNonNull(at, "settledAt");
        DomainPreconditions.requireNonNull(context, "eventContext");
        requireUsable(at);
        DomainPreconditions.require(reservedQuantity.unit().equals(actualUsage.unit()),
                DomainErrorCode.INVALID_ARGUMENT, "settlement unit differs from reservation unit");
        DomainPreconditions.require(!actualUsage.isGreaterThan(reservedQuantity), DomainErrorCode.USAGE_EXCEEDED,
                "actual usage exceeds reservation; an approved reconciliation rule is required");
        settledQuantity = Optional.of(actualUsage);
        state = UsageReservationState.SETTLED;
        bump("billing.reservation.settled", context,
                Map.of("unit", actualUsage.unit(),
                        "settlementId", settlementId.value(),
                        "ruleVersion", settlementRuleVersion));
        return new UsageSettlement(
                settlementId,
                tenantId,
                userId,
                id,
                businessOperationId,
                reservedQuantity,
                actualUsage,
                reservedQuantity.minus(actualUsage),
                settlementRuleVersion,
                at);
    }

    public void release(String reasonCode, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == UsageReservationState.RESERVED,
                DomainErrorCode.RESERVATION_ALREADY_FINALIZED, "reservation is already finalized");
        String checkedReasonCode = DomainPreconditions.requireText(reasonCode, "releaseReasonCode");
        DomainPreconditions.require(checkedReasonCode.matches("[A-Z][A-Z0-9_]{0,63}"),
                DomainErrorCode.INVALID_ARGUMENT, "release reason code is invalid");
        DomainPreconditions.requireNonNull(context, "eventContext");
        releaseReasonCode = Optional.of(checkedReasonCode);
        state = UsageReservationState.RELEASED;
        bump("billing.reservation.released", context,
                Map.of("reasonCode", releaseReasonCode.orElseThrow()));
    }

    public void expire(AggregateVersion expectedVersion, Instant at, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == UsageReservationState.RESERVED,
                DomainErrorCode.RESERVATION_ALREADY_FINALIZED, "reservation is already finalized");
        DomainPreconditions.require(!at.isBefore(expiresAt), DomainErrorCode.INVALID_STATE,
                "reservation cannot expire before expiresAt");
        state = UsageReservationState.EXPIRED;
        bump("billing.reservation.expired", context, Map.of());
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }
}
