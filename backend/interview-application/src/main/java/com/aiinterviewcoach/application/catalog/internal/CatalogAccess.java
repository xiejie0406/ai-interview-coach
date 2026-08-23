package com.aiinterviewcoach.application.catalog.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.identity.MembershipRole;

/** Catalog 写操作的最小角色门；发布与审核不能由普通成员绕过。 */
public final class CatalogAccess {

    private final ActivePrincipalGuard principal;

    public CatalogAccess(ActivePrincipalGuard principal) {
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    public void requireEditor(OperationContext context) {
        principal.requireRole(context, MembershipRole.OWNER, MembershipRole.CONTENT_ADMIN,
                MembershipRole.SUPER_ADMIN);
    }

    public void requireReviewer(OperationContext context) {
        principal.requireRole(context, MembershipRole.CONTENT_ADMIN, MembershipRole.SUPER_ADMIN,
                MembershipRole.OPS_ADMIN);
    }
}
