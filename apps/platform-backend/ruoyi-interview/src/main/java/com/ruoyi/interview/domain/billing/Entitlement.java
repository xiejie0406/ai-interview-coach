package com.ruoyi.interview.domain.billing;

import com.ruoyi.interview.domain.platform.AggregateRoot;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Map;

/**
 * 用户权益事实。预留和结算只使用确定性数量；Provider 成本由独立成本账记录。
 */
public final class Entitlement extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final ResourceId productPlanId;
    private final EntitlementSource source;
    private final Instant validFrom;
    private final Instant validTo;
    private final UsageQuantity limit;
    private UsageQuantity consumed;
    private UsageQuantity reserved;
    private EntitlementState state;
    private AggregateVersion version;

    private Entitlement(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId productPlanId,
            EntitlementSource source,
            Instant validFrom,
            Instant validTo,
            UsageQuantity limit,
            UsageQuantity consumed,
            UsageQuantity reserved,
            EntitlementState state,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "entitlementId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.productPlanId = DomainPreconditions.requireNonNull(productPlanId, "productPlanId");
        this.source = DomainPreconditions.requireNonNull(source, "entitlementSource");
        this.validFrom = DomainPreconditions.requireNonNull(validFrom, "validFrom");
        this.validTo = DomainPreconditions.requireNonNull(validTo, "validTo");
        DomainPreconditions.require(validTo.isAfter(validFrom), DomainErrorCode.INVALID_ARGUMENT,
                "entitlement validTo must be after validFrom");
        this.limit = DomainPreconditions.requireNonNull(limit, "entitlementLimit");
        this.consumed = DomainPreconditions.requireNonNull(consumed, "consumedUsage");
        this.reserved = DomainPreconditions.requireNonNull(reserved, "reservedUsage");
        this.state = DomainPreconditions.requireNonNull(state, "entitlementState");
        this.version = DomainPreconditions.requireNonNull(version, "entitlementVersion");
        assertSameUnit(limit, consumed, reserved);
        DomainPreconditions.require(!consumed.plus(reserved).isGreaterThan(limit), DomainErrorCode.USAGE_EXCEEDED,
                "entitlement consumed plus reserved exceeds limit");
        if (state == EntitlementState.PENDING) {
            DomainPreconditions.require(consumed.isZero() && reserved.isZero(), DomainErrorCode.INVALID_STATE,
                    "pending entitlement cannot contain usage");
        }
        if (state == EntitlementState.EXHAUSTED) {
            DomainPreconditions.require(consumed.value().compareTo(limit.value()) == 0 && reserved.isZero(),
                    DomainErrorCode.INVALID_STATE, "exhausted entitlement must be fully consumed");
        }
        if (state == EntitlementState.EXPIRED || state == EntitlementState.REVOKED) {
            DomainPreconditions.require(reserved.isZero(), DomainErrorCode.INVALID_STATE,
                    "final entitlement cannot retain reservations");
        }
    }

    public static Entitlement grant(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId productPlanId,
            EntitlementSource source,
            Instant validFrom,
            Instant validTo,
            UsageQuantity limit,
            EventContext context
    ) {
        Entitlement entitlement = new Entitlement(id, tenantId, userId, productPlanId, source,
                validFrom, validTo, limit, UsageQuantity.zero(limit.unit()), UsageQuantity.zero(limit.unit()),
                EntitlementState.PENDING, AggregateVersion.initial());
        entitlement.recordEvent("billing.entitlement.granted", tenantId, id, entitlement.version, context,
                Map.of("source", source.name(), "unit", limit.unit()));
        return entitlement;
    }

    /** 从 tenant-scoped persistence 重建；不产生事件。 */
    public static Entitlement rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            ResourceId productPlanId,
            EntitlementSource source,
            Instant validFrom,
            Instant validTo,
            UsageQuantity limit,
            UsageQuantity consumed,
            UsageQuantity reserved,
            EntitlementState state,
            AggregateVersion version
    ) {
        return new Entitlement(id, tenantId, userId, productPlanId, source, validFrom, validTo,
                limit, consumed, reserved, state, version);
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

    public ResourceId productPlanId() {
        return productPlanId;
    }

    public EntitlementSource source() {
        return source;
    }

    public Instant validFrom() {
        return validFrom;
    }

    public Instant validTo() {
        return validTo;
    }

    public UsageQuantity limit() {
        return limit;
    }

    public UsageQuantity consumed() {
        return consumed;
    }

    public UsageQuantity reserved() {
        return reserved;
    }

    public UsageQuantity available() {
        return limit.minus(consumed.plus(reserved));
    }

    public EntitlementState state() {
        return state;
    }

    public AggregateVersion version() {
        return version;
    }

    public void activate(AggregateVersion expectedVersion, Instant at, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == EntitlementState.PENDING, DomainErrorCode.INVALID_STATE,
                "only pending entitlement can be activated");
        DomainPreconditions.require(!at.isBefore(validFrom) && at.isBefore(validTo), DomainErrorCode.ENTITLEMENT_EXPIRED,
                "entitlement is outside its validity window");
        state = EntitlementState.ACTIVE;
        bump("billing.entitlement.activated", context, Map.of());
    }

    public void reserve(UsageQuantity quantity, AggregateVersion expectedVersion, Instant at, EventContext context) {
        expectedVersion(expectedVersion);
        requireUsable(at);
        assertSameUnit(limit, quantity);
        DomainPreconditions.require(!quantity.isZero(), DomainErrorCode.INVALID_ARGUMENT,
                "reservation quantity must be positive");
        DomainPreconditions.require(!quantity.isGreaterThan(available()), DomainErrorCode.USAGE_EXCEEDED,
                "entitlement does not have enough available usage");
        reserved = reserved.plus(quantity);
        bump("billing.usage.reserved", context, Map.of("unit", quantity.unit()));
    }

    public void settle(
            UsageQuantity originallyReserved,
            UsageQuantity actualUsage,
            AggregateVersion expectedVersion,
            Instant at,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.requireNonNull(at, "settledAt");
        DomainPreconditions.require(state == EntitlementState.ACTIVE,
                DomainErrorCode.ENTITLEMENT_NOT_ACTIVE,
                "entitlement is not active for settlement");
        assertSameUnit(limit, originallyReserved, actualUsage);
        DomainPreconditions.require(!originallyReserved.isGreaterThan(reserved), DomainErrorCode.INVALID_STATE,
                "settlement exceeds outstanding reserved usage");
        DomainPreconditions.require(!actualUsage.isGreaterThan(originallyReserved), DomainErrorCode.USAGE_EXCEEDED,
                "actual usage exceeds reservation; reconciliation policy is required");
        reserved = reserved.minus(originallyReserved);
        consumed = consumed.plus(actualUsage);
        DomainPreconditions.require(!consumed.isGreaterThan(limit), DomainErrorCode.USAGE_EXCEEDED,
                "settlement exceeds entitlement limit");
        if (consumed.value().compareTo(limit.value()) == 0) {
            state = EntitlementState.EXHAUSTED;
        }
        bump("billing.usage.settled", context, Map.of("unit", actualUsage.unit()));
    }

    public void release(
            UsageQuantity originallyReserved,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(state == EntitlementState.ACTIVE,
                DomainErrorCode.ENTITLEMENT_NOT_ACTIVE,
                "entitlement is not active for release");
        assertSameUnit(limit, originallyReserved);
        DomainPreconditions.require(!originallyReserved.isZero(), DomainErrorCode.INVALID_ARGUMENT,
                "released reservation quantity must be positive");
        DomainPreconditions.require(!originallyReserved.isGreaterThan(reserved), DomainErrorCode.INVALID_STATE,
                "release exceeds outstanding reserved usage");
        reserved = reserved.minus(originallyReserved);
        bump("billing.usage.released", context, Map.of("unit", originallyReserved.unit()));
    }

    public void expire(AggregateVersion expectedVersion, Instant at, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(!at.isBefore(validTo), DomainErrorCode.INVALID_STATE,
                "entitlement cannot expire before validTo");
        DomainPreconditions.require(reserved.isZero(), DomainErrorCode.INVALID_STATE,
                "entitlement with outstanding reservations cannot expire");
        DomainPreconditions.require(state == EntitlementState.ACTIVE || state == EntitlementState.PENDING,
                DomainErrorCode.INVALID_STATE, "entitlement cannot expire from current state");
        state = EntitlementState.EXPIRED;
        bump("billing.entitlement.expired", context, Map.of());
    }

    public void revoke(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(reserved.isZero(), DomainErrorCode.INVALID_STATE,
                "entitlement with outstanding reservations cannot be revoked without reconciliation");
        DomainPreconditions.require(state != EntitlementState.EXPIRED && state != EntitlementState.REVOKED,
                DomainErrorCode.INVALID_STATE, "entitlement is already final");
        state = EntitlementState.REVOKED;
        bump("billing.entitlement.revoked", context, Map.of());
    }

    private void requireUsable(Instant at) {
        DomainPreconditions.requireNonNull(at, "usageCheckAt");
        DomainPreconditions.require(state == EntitlementState.ACTIVE, DomainErrorCode.ENTITLEMENT_NOT_ACTIVE,
                "entitlement is not active");
        DomainPreconditions.require(!at.isBefore(validFrom) && at.isBefore(validTo), DomainErrorCode.ENTITLEMENT_EXPIRED,
                "entitlement is outside its validity window");
    }

    private void bump(String eventType, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, attributes);
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private static void assertSameUnit(UsageQuantity first, UsageQuantity... others) {
        DomainPreconditions.requireNonNull(first, "usageQuantity");
        for (UsageQuantity other : others) {
            DomainPreconditions.requireNonNull(other, "usageQuantity");
            DomainPreconditions.require(first.unit().equals(other.unit()), DomainErrorCode.INVALID_ARGUMENT,
                    "usage units must match");
        }
    }
}
