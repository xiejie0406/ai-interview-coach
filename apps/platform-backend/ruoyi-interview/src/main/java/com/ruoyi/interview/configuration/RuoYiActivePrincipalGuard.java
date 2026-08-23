package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.security.BusinessTenantResolver;
import com.ruoyi.interview.application.security.PrincipalRef;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.governance.BusinessRole;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 将 application 主体边界绑定到 RuoYi SecurityContext。
 * 该组件不读取请求中的 user_id、role 或 tenant，也不创建新的登录态。
 */
@Component
@ConditionalOnBean(BusinessTenantResolver.class)
public final class RuoYiActivePrincipalGuard implements ActivePrincipalGuard {

    private final RuoYiPrincipalFacade principals;
    private final BusinessTenantResolver tenants;

    public RuoYiActivePrincipalGuard(RuoYiPrincipalFacade principals, BusinessTenantResolver tenants) {
        this.principals = principals;
        this.tenants = tenants;
    }

    @Override
    public com.ruoyi.interview.domain.platform.PrincipalRef requireActive(OperationContext context) {
        var current = currentDomainPrincipal();
        var requested = context.requirePrincipal();
        if (!current.equals(requested)) {
            throw forbidden("request principal does not match RuoYi SecurityContext");
        }
        return current;
    }

    @Override
    public com.ruoyi.interview.domain.platform.PrincipalRef requireActive(
            com.ruoyi.interview.domain.platform.PrincipalRef principal) {
        var current = currentDomainPrincipal();
        if (!current.equals(principal)) {
            throw forbidden("principal does not match RuoYi SecurityContext");
        }
        return current;
    }

    @Override
    public void requireRole(OperationContext context, BusinessRole... allowedRoles) {
        requireActive(context);
        Set<String> permissions = principals.requiredPrincipal().permissions();
        boolean allowed = Set.of(allowedRoles).stream()
                .map(ROLE_PERMISSIONS::get)
                .filter(java.util.Objects::nonNull)
                .flatMap(Set::stream)
                .anyMatch(permissions::contains);
        if (!allowed) {
            throw forbidden("RuoYi permission is required");
        }
    }

    private com.ruoyi.interview.domain.platform.PrincipalRef currentDomainPrincipal() {
        PrincipalRef current = principals.requiredPrincipal();
        return new com.ruoyi.interview.domain.platform.PrincipalRef(
                tenants.resolveFor(current.userId()), UserId.of(Long.toString(current.userId())));
    }

    private static ApplicationException forbidden(String message) {
        return new ApplicationException(ApplicationErrorCode.FORBIDDEN, message, false, Map.of());
    }

    private static final Map<BusinessRole, Set<String>> ROLE_PERMISSIONS = Map.of(
            BusinessRole.OWNER, Set.of(InterviewPermissionCodes.SESSION_START,
                    InterviewPermissionCodes.SESSION_EDIT, InterviewPermissionCodes.REPORT_VIEW),
            BusinessRole.MEMBER, Set.of(InterviewPermissionCodes.SESSION_START,
                    InterviewPermissionCodes.REPORT_VIEW),
            BusinessRole.CONTENT_ADMIN, Set.of(InterviewPermissionCodes.QUESTION_LIST,
                    InterviewPermissionCodes.QUESTION_ADD, InterviewPermissionCodes.QUESTION_EDIT),
            BusinessRole.OPS_ADMIN, Set.of(InterviewPermissionCodes.REPORT_VIEW,
                    InterviewPermissionCodes.REPORT_EXPORT),
            BusinessRole.SUPPORT, Set.of(InterviewPermissionCodes.REPORT_VIEW),
            BusinessRole.PRIVACY_AUDITOR, Set.of(InterviewPermissionCodes.REPORT_VIEW),
            BusinessRole.SUPER_ADMIN, Set.of("*")
    );
}
