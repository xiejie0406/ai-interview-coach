package com.ruoyi.aps.api.controller;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceManagementService;
import com.ruoyi.aps.application.resource.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApsResourceHttpAuthorizationTest
{
    @Test
    void deniedWorkshopUserGets403BeforeRepositoryAccess() throws Exception
    {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext())
        {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("aps.enabled=true", "aps.api.enabled=true", "aps.persistence.enabled=true")
                    .applyTo(context);
            context.register(TestWebSecurity.class);
            context.refresh();

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mockMvc.perform(get("/api/aps/v1/resources/workshops").with(user("outsider")))
                    .andExpect(status().isForbidden());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import(ApsResourceController.class)
    static class TestWebSecurity
    {
        @Bean(name = "ss")
        ApsSystemAuthorizationTest.DenyPermissionService denyPermissionService()
        {
            return new ApsSystemAuthorizationTest.DenyPermissionService();
        }

        @Bean
        ResourceManagementService resourceManagementService()
        {
            ResourceRepository repository = (ResourceRepository) Proxy.newProxyInstance(
                    ResourceRepository.class.getClassLoader(), new Class<?>[] { ResourceRepository.class },
                    (proxy, method, arguments) -> {
                        throw new AssertionError("权限拒绝前不应访问资源仓储");
                    });
            ApsTransactionOperations transactions = new ApsTransactionOperations()
            {
                @Override
                public <T> T required(Supplier<T> action)
                {
                    throw new AssertionError("权限拒绝前不应开启 APS 事务");
                }
            };
            return new ResourceManagementService(repository, transactions);
        }

        @Bean
        ApsAuditActorProvider actorProvider()
        {
            return () -> new ApsAuditActor("300", "outsider");
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception
        {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }
    }
}
