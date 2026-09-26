package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.application.security.AdenOperatorPrincipalProvider;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class RuoYiAdenOperatorPrincipalProvider implements AdenOperatorPrincipalProvider {
    @Override
    public AdenOperatorPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)
                || loginUser.getUserId() == null) {
            throw new AdenApplicationException("ADEN_AUTH_REQUIRED", "请先登录后再继续");
        }
        return new AdenOperatorPrincipal(
                loginUser.getUserId(),
                loginUser.getUsername(),
                loginUser.getPermissions());
    }
}
