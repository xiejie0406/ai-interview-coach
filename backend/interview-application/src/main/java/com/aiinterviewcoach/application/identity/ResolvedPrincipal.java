package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.domain.identity.MembershipRole;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.PrincipalRef;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Set;

/** 由可信 session 解析出的 principal；客户端不能提交 tenant 或角色覆盖它。 */
public record ResolvedPrincipal(
        PrincipalRef principalRef,
        ResourceId sessionId,
        Set<MembershipRole> roles,
        Instant expiresAt
) {

    public ResolvedPrincipal {
        DomainPreconditions.requireNonNull(principalRef, "principalRef");
        DomainPreconditions.requireNonNull(sessionId, "sessionId");
        roles = Set.copyOf(DomainPreconditions.requireNonEmpty(roles, "roles"));
        DomainPreconditions.requireNonNull(expiresAt, "sessionExpiresAt");
    }

    public boolean hasRole(MembershipRole role) {
        return roles.contains(role);
    }
}
