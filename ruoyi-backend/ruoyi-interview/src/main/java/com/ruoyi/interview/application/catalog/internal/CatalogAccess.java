package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.governance.BusinessRole;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.TenantId;

/** Catalog 读写/审核操作的最小角色门；HTTP 权限之外应用层仍需再次校验。 */
public final class CatalogAccess {

    private final ActivePrincipalGuard principal;

    public CatalogAccess(ActivePrincipalGuard principal) {
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    /**
     * 允许创建/编辑题库正文的角色。OWNER 只代表个人面试工作区，不应因此获得公共题库写权限。
     */
    public void requireEditor(OperationContext context) {
        principal.requireRole(context, BusinessRole.CONTENT_ADMIN, BusinessRole.SUPER_ADMIN);
    }

    /**
     * 允许读取 Admin 题库治理投影的角色。OPS_ADMIN 可查看治理状态，但不能借此写入内容。
     */
    public void requireReader(OperationContext context) {
        principal.requireRole(context, BusinessRole.CONTENT_ADMIN, BusinessRole.OPS_ADMIN,
                BusinessRole.SUPER_ADMIN);
    }

    /**
     * 校验服务端传入的目标题库租户，再按当前 RuoYi 主体执行 RBAC。
     * 目标租户不能从 OperationContext 的主体租户推导：公共题库通常是独立的 platform tenant。
     */
    public TenantId requireCatalogTenant(TenantId catalogTenantId) {
        return DomainPreconditions.requireNonNull(catalogTenantId, "catalogTenantId");
    }

    public void requireEditor(TenantId catalogTenantId, OperationContext context) {
        requireCatalogTenant(catalogTenantId);
        requireEditor(context);
    }

    public void requireReader(TenantId catalogTenantId, OperationContext context) {
        requireCatalogTenant(catalogTenantId);
        requireReader(context);
    }

    public void requireReviewer(OperationContext context) {
        principal.requireRole(context, BusinessRole.CONTENT_ADMIN, BusinessRole.SUPER_ADMIN,
                BusinessRole.OPS_ADMIN);
    }

    public void requireReviewer(TenantId catalogTenantId, OperationContext context) {
        requireCatalogTenant(catalogTenantId);
        requireReviewer(context);
    }
}
