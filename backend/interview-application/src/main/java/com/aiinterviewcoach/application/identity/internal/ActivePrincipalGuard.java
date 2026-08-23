package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.identity.IdentityPolicy;
import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.identity.MembershipRole;
import com.aiinterviewcoach.domain.identity.Tenant;
import com.aiinterviewcoach.domain.identity.UserAccount;
import com.aiinterviewcoach.domain.platform.PrincipalRef;

import java.util.Arrays;
import java.util.Map;

/** 每个受保护用例重新核对 tenant、account 和 membership；不信任客户端角色。 */
public final class ActivePrincipalGuard implements com.aiinterviewcoach.application.identity.ActivePrincipalGuard {

    private final IdentityRepository repository;

    public ActivePrincipalGuard(IdentityRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    @Override
    public PrincipalRef requireActive(OperationContext context) {
        return requireActive(context.requirePrincipal());
    }

    @Override
    public PrincipalRef requireActive(PrincipalRef principal) {
        UserAccount account = repository.findUser(principal.userId()).orElseThrow(() -> notFound());
        Tenant tenant = repository.findTenant(principal.tenantId()).orElseThrow(() -> notFound());
        Membership membership = repository.findMembership(principal.tenantId(), principal.userId())
                .orElseThrow(() -> notFound());
        IdentityPolicy.requireActivePrincipal(tenant, account, membership);
        return principal;
    }

    @Override
    public void requireRole(OperationContext context, MembershipRole... allowedRoles) {
        PrincipalRef principal = requireActive(context);
        Membership membership = repository.findMembership(principal.tenantId(), principal.userId())
                .orElseThrow(() -> notFound());
        boolean allowed = Arrays.stream(allowedRoles).anyMatch(role -> role == membership.role());
        if (!allowed) {
            throw new ApplicationException(ApplicationErrorCode.FORBIDDEN,
                    "principal is not allowed to perform this operation", false,
                    Map.of("requiredRole", Arrays.toString(allowedRoles)));
        }
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "principal was not found", false, Map.of());
    }
}
