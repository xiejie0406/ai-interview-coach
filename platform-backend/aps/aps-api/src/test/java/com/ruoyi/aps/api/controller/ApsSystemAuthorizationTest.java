package com.ruoyi.aps.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApsSystemAuthorizationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("aps.enabled=true", "aps.api.enabled=true")
            .withUserConfiguration(DenyAllMethodSecurity.class);

    @Test
    void deniedRuoyiPermissionCannotInvokeProtectedApsApi()
    {
        contextRunner.run(context -> {
            ApsSystemController controller = context.getBean(ApsSystemController.class);

            assertThatThrownBy(controller::capabilities).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(ApsSystemController.class)
    static class DenyAllMethodSecurity
    {
        @Bean(name = "ss")
        DenyPermissionService denyPermissionService()
        {
            return new DenyPermissionService();
        }
    }

    static class DenyPermissionService
    {
        public boolean hasPermi(String permission)
        {
            return false;
        }
    }
}
