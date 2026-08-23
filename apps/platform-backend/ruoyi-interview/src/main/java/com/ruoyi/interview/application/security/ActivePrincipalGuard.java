package com.ruoyi.interview.application.security;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.governance.BusinessRole;
import com.ruoyi.interview.domain.platform.PrincipalRef;

/**
 * 业务用例需要的主体边界。
 * 实现必须由 RuoYi SecurityContext 提供主体，并通过业务 tenant 适配器补充工作区范围。
 */
public interface ActivePrincipalGuard {

    PrincipalRef requireActive(OperationContext context);

    PrincipalRef requireActive(PrincipalRef principal);

    void requireRole(OperationContext context, BusinessRole... allowedRoles);
}
