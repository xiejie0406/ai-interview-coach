package com.aiinterviewcoach.domain.identity;

import com.aiinterviewcoach.domain.platform.AggregateRoot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Map;
import java.util.Optional;

/** personal tenant 从首条私有数据开始存在；platform tenant 只承载公开内容/系统配置。 */
public final class Tenant extends AggregateRoot {

    private final TenantId id;
    private final TenantType type;
    private final UserId createdByUserId;
    private final UserId ownerUserId;
    private final String displayName;
    private TenantStatus status;
    private AggregateVersion version;

    private Tenant(
            TenantId id,
            TenantType type,
            UserId createdByUserId,
            UserId ownerUserId,
            String displayName,
            TenantStatus status,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "tenantId");
        this.type = DomainPreconditions.requireNonNull(type, "tenantType");
        this.createdByUserId = DomainPreconditions.requireNonNull(createdByUserId, "createdByUserId");
        this.ownerUserId = ownerUserId;
        if (type == TenantType.PERSONAL) {
            DomainPreconditions.requireNonNull(ownerUserId, "personal tenant ownerUserId");
        }
        this.displayName = DomainPreconditions.requireText(displayName, "displayName");
        this.status = DomainPreconditions.requireNonNull(status, "tenantStatus");
        this.version = DomainPreconditions.requireNonNull(version, "tenantVersion");
    }

    public static Tenant createPersonal(TenantId id, UserId ownerUserId, String displayName, EventContext context) {
        Tenant tenant = new Tenant(id, TenantType.PERSONAL, ownerUserId, ownerUserId, displayName,
                TenantStatus.ACTIVE, AggregateVersion.initial());
        tenant.recordEvent("identity.tenant.created", id, tenant.resourceId(), tenant.version, context,
                Map.of("tenantType", TenantType.PERSONAL.name(), "ownerUserId", ownerUserId.value().toString()));
        return tenant;
    }

    public static Tenant createPlatform(TenantId id, UserId createdByUserId, String displayName, EventContext context) {
        Tenant tenant = new Tenant(id, TenantType.PLATFORM, createdByUserId, null, displayName,
                TenantStatus.ACTIVE, AggregateVersion.initial());
        tenant.recordEvent("identity.tenant.created", id, tenant.resourceId(), tenant.version, context,
                Map.of("tenantType", TenantType.PLATFORM.name()));
        return tenant;
    }

    /** 从持久化事实重建；调用方必须已经按 tenant scope 读取。 */
    public static Tenant rehydrate(
            TenantId id,
            TenantType type,
            UserId createdByUserId,
            UserId ownerUserId,
            String displayName,
            TenantStatus status,
            AggregateVersion version
    ) {
        return new Tenant(id, type, createdByUserId, ownerUserId, displayName, status, version);
    }

    public TenantId id() {
        return id;
    }

    public TenantType type() {
        return type;
    }

    public UserId createdByUserId() {
        return createdByUserId;
    }

    public Optional<UserId> ownerUserId() {
        return Optional.ofNullable(ownerUserId);
    }

    public String displayName() {
        return displayName;
    }

    public TenantStatus status() {
        return status;
    }

    public AggregateVersion version() {
        return version;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    public boolean owns(UserId userId) {
        return ownerUserId != null && ownerUserId.equals(userId);
    }

    public void requireActive() {
        DomainPreconditions.require(status == TenantStatus.ACTIVE, DomainErrorCode.RESOURCE_NOT_ACTIVE,
                "tenant is not active");
    }

    public void suspend(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == TenantStatus.ACTIVE, DomainErrorCode.INVALID_STATE,
                "only an active tenant can be suspended");
        transition(TenantStatus.SUSPENDED, "identity.tenant.suspended", context);
    }

    public void reactivate(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == TenantStatus.SUSPENDED, DomainErrorCode.INVALID_STATE,
                "only a suspended tenant can be reactivated");
        transition(TenantStatus.ACTIVE, "identity.tenant.reactivated", context);
    }

    public void beginClosing(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == TenantStatus.ACTIVE || status == TenantStatus.SUSPENDED,
                DomainErrorCode.INVALID_STATE, "tenant cannot begin closing from current state");
        transition(TenantStatus.CLOSING, "identity.tenant.closing_started", context);
    }

    public void close(AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == TenantStatus.CLOSING, DomainErrorCode.INVALID_STATE,
                "tenant must be closing before it can be closed");
        transition(TenantStatus.CLOSED, "identity.tenant.closed", context);
    }

    private void transition(TenantStatus next, String eventType, EventContext context) {
        status = next;
        version = version.next();
        recordEvent(eventType, id, resourceId(), version, context, Map.of("status", status.name()));
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private ResourceId resourceId() {
        return ResourceId.of(id.value());
    }
}
