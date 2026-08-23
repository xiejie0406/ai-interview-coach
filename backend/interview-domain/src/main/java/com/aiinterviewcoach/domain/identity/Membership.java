package com.aiinterviewcoach.domain.identity;

import com.aiinterviewcoach.domain.platform.AggregateRoot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.Map;

/** tenant 成员资格；每个受保护资源仍须由应用层重新校验 owner/capability。 */
public final class Membership extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final MembershipRole role;
    private final Instant joinedAt;
    private MembershipStatus status;
    private Instant leftAt;
    private AggregateVersion version;

    private Membership(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            MembershipRole role,
            Instant joinedAt,
            MembershipStatus status,
            Instant leftAt,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "membershipId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.userId = DomainPreconditions.requireNonNull(userId, "userId");
        this.role = DomainPreconditions.requireNonNull(role, "membershipRole");
        this.joinedAt = DomainPreconditions.requireNonNull(joinedAt, "joinedAt");
        this.status = DomainPreconditions.requireNonNull(status, "membershipStatus");
        this.leftAt = leftAt;
        this.version = DomainPreconditions.requireNonNull(version, "membershipVersion");
        if (status == MembershipStatus.LEFT) {
            DomainPreconditions.requireNonNull(leftAt, "leftAt");
        }
    }

    public static Membership join(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            MembershipRole role,
            Instant joinedAt,
            EventContext context
    ) {
        Membership membership = new Membership(id, tenantId, userId, role, joinedAt,
                MembershipStatus.ACTIVE, null, AggregateVersion.initial());
        membership.recordEvent("identity.membership.joined", tenantId, id, membership.version, context,
                Map.of("userId", userId.value().toString(), "role", role.name()));
        return membership;
    }

    /** 从持久化事实重建；不产生新的领域事件。 */
    public static Membership rehydrate(
            ResourceId id,
            TenantId tenantId,
            UserId userId,
            MembershipRole role,
            Instant joinedAt,
            MembershipStatus status,
            Instant leftAt,
            AggregateVersion version
    ) {
        return new Membership(id, tenantId, userId, role, joinedAt, status, leftAt, version);
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

    public MembershipRole role() {
        return role;
    }

    public MembershipStatus status() {
        return status;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant leftAt() {
        return leftAt;
    }

    public AggregateVersion version() {
        return version;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public void requireActive() {
        DomainPreconditions.require(status == MembershipStatus.ACTIVE, DomainErrorCode.MEMBERSHIP_NOT_ACTIVE,
                "membership is not active");
    }

    public void suspend(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == MembershipStatus.ACTIVE, DomainErrorCode.INVALID_STATE,
                "only active membership can be suspended");
        DomainPreconditions.require(role != MembershipRole.OWNER, DomainErrorCode.POLICY_DENIED,
                "personal owner membership cannot be suspended without an approved ownership transition");
        transition(MembershipStatus.SUSPENDED, "identity.membership.suspended", context);
    }

    public void reactivate(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == MembershipStatus.SUSPENDED, DomainErrorCode.INVALID_STATE,
                "only suspended membership can be reactivated");
        transition(MembershipStatus.ACTIVE, "identity.membership.reactivated", context);
    }

    public void leave(AggregateVersion expectedVersion, Instant leftAt, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status != MembershipStatus.LEFT, DomainErrorCode.INVALID_STATE,
                "membership has already left");
        DomainPreconditions.require(role != MembershipRole.OWNER, DomainErrorCode.POLICY_DENIED,
                "personal owner membership cannot leave without tenant closure or ownership transfer");
        this.leftAt = DomainPreconditions.requireNonNull(leftAt, "leftAt");
        transition(MembershipStatus.LEFT, "identity.membership.left", context);
    }

    private void transition(MembershipStatus next, String eventType, EventContext context) {
        status = next;
        version = version.next();
        recordEvent(eventType, tenantId, id, version, context, Map.of("status", status.name()));
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }
}
