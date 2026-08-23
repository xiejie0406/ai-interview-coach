package com.ruoyi.interview.configuration;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.interview.application.security.BusinessTenantResolver;
import com.ruoyi.interview.application.security.PrincipalRef;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.stereotype.Component;

/**
 * 将 RuoYi 当前请求主体转换为 AI application 层的稳定值对象。
 * userId、权限和部门均来自 SecurityContext，不接受前端覆盖。
 */
@Component
public class RuoYiPrincipalFacade {

    public PrincipalRef requiredPrincipal() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return new PrincipalRef(
                loginUser.getUserId(),
                loginUser.getDeptId(),
                loginUser.getUsername(),
                loginUser.getPermissions());
    }

    /**
     * 将当前 RuoYi 主体转换为领域主体；tenant 只能由服务端 resolver 提供。
     */
    public com.ruoyi.interview.domain.platform.PrincipalRef requiredDomainPrincipal(
            BusinessTenantResolver tenantResolver) {
        PrincipalRef principal = requiredPrincipal();
        TenantId tenantId = tenantResolver.resolveFor(principal.userId());
        return new com.ruoyi.interview.domain.platform.PrincipalRef(
                tenantId,
                UserId.of(Long.toString(principal.userId())));
    }
}
