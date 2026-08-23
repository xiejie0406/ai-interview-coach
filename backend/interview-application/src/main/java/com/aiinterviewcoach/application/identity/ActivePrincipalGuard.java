package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.identity.MembershipRole;
import com.aiinterviewcoach.domain.platform.PrincipalRef;

/**
 * Identity 拥有的公共应用边界。其他逻辑域只能请求活跃主体/角色决定，不能读取 IdentityRepository。
 */
public interface ActivePrincipalGuard {

    PrincipalRef requireActive(OperationContext context);

    PrincipalRef requireActive(PrincipalRef principal);

    void requireRole(OperationContext context, MembershipRole... allowedRoles);
}

