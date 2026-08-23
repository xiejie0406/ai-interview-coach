package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.governance.BusinessRole;

/** Catalog 写操作的最小角色门；发布与审核不能由普通成员绕过。 */
public final class CatalogAccess {

    private final ActivePrincipalGuard principal;

    public CatalogAccess(ActivePrincipalGuard principal) {
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    public void requireEditor(OperationContext context) {
        principal.requireRole(context, BusinessRole.OWNER, BusinessRole.CONTENT_ADMIN,
                BusinessRole.SUPER_ADMIN);
    }

    public void requireReviewer(OperationContext context) {
        principal.requireRole(context, BusinessRole.CONTENT_ADMIN, BusinessRole.SUPER_ADMIN,
                BusinessRole.OPS_ADMIN);
    }
}
