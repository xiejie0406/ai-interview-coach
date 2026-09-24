package com.ruoyi.aps.api.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApsSystemHttpAuthorizationTest
{
    @Test
    void authenticatedUserWithoutRuoyiPermissionGetsHttp403() throws Exception
    {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext())
        {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("aps.enabled=true", "aps.api.enabled=true").applyTo(context);
            context.register(TestWebSecurity.class);
            context.refresh();

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();

            mockMvc.perform(get("/api/aps/v1/health"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/aps/v1/capabilities").with(user("planner")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/aps/v1/validations")
                            .header("Idempotency-Key", "denied-validation")
                            .contentType("application/json")
                            .content("{}")
                            .with(user("planner")))
                    .andExpect(status().isForbidden());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({ApsSystemController.class, ApsValidationController.class})
    static class TestWebSecurity
    {
        @Bean(name = "ss")
        ApsSystemAuthorizationTest.DenyPermissionService denyPermissionService()
        {
            return new ApsSystemAuthorizationTest.DenyPermissionService();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception
        {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
    }
}
