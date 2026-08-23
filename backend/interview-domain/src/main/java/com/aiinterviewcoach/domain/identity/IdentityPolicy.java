package com.aiinterviewcoach.domain.identity;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

/** Identity/tenant 的确定性规则，供 application 在每个资源命令前调用。 */
public final class IdentityPolicy {

    private IdentityPolicy() {
    }

    public static void requirePersonalOwner(Tenant tenant, Membership membership) {
        DomainPreconditions.requireNonNull(tenant, "tenant");
        DomainPreconditions.requireNonNull(membership, "membership");
        DomainPreconditions.require(tenant.type() == TenantType.PERSONAL,
                DomainErrorCode.POLICY_DENIED, "owner membership requires a personal tenant");
        DomainPreconditions.require(tenant.id().equals(membership.tenantId())
                        && tenant.owns(membership.userId())
                        && membership.role() == MembershipRole.OWNER
                        && membership.isActive(),
                DomainErrorCode.OWNERSHIP_DENIED, "membership is not the active personal tenant owner");
    }

    public static void requireActivePrincipal(Tenant tenant, UserAccount account, Membership membership) {
        DomainPreconditions.requireNonNull(tenant, "tenant");
        DomainPreconditions.requireNonNull(account, "account");
        DomainPreconditions.requireNonNull(membership, "membership");
        tenant.requireActive();
        account.requireActive();
        DomainPreconditions.require(membership.tenantId().equals(tenant.id())
                        && membership.userId().equals(account.id())
                        && membership.isActive(),
                DomainErrorCode.OWNERSHIP_DENIED, "principal is not an active member of the tenant");
    }

    public static void requireSameTenant(TenantId expectedTenantId, TenantId actualTenantId) {
        DomainPreconditions.requireNonNull(expectedTenantId, "expectedTenantId");
        DomainPreconditions.requireNonNull(actualTenantId, "actualTenantId");
        DomainPreconditions.require(expectedTenantId.equals(actualTenantId), DomainErrorCode.TENANT_MISMATCH,
                "resource belongs to another tenant");
    }

    public static void requireSameUser(UserId expectedUserId, UserId actualUserId) {
        DomainPreconditions.requireNonNull(expectedUserId, "expectedUserId");
        DomainPreconditions.requireNonNull(actualUserId, "actualUserId");
        DomainPreconditions.require(expectedUserId.equals(actualUserId), DomainErrorCode.OWNERSHIP_DENIED,
                "resource belongs to another user");
    }
}
