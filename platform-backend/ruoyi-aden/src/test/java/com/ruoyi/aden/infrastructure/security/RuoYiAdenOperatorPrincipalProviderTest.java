package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuoYiAdenOperatorPrincipalProviderTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adaptsOnlyServerAuthenticatedLoginUserFields() {
        SysUser user = new SysUser();
        user.setUserName("operator");
        LoginUser loginUser = new LoginUser(42L, 7L, user, Set.of("aden:workspace:list"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));

        var principal = new RuoYiAdenOperatorPrincipalProvider().current();

        assertEquals(42, principal.userId());
        assertEquals("operator", principal.username());
        assertEquals(Set.of("aden:workspace:list"), principal.permissions());
    }

    @Test
    void rejectsAnyNonRuoYiPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("untrusted", null));

        assertThrows(AdenApplicationException.class,
                () -> new RuoYiAdenOperatorPrincipalProvider().current());
    }
}
