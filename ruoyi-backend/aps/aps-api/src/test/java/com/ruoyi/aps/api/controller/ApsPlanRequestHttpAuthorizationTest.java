package com.ruoyi.aps.api.controller;

import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.planning.PlanRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApsPlanRequestHttpAuthorizationTest
{
    private static final String REQUEST_ID = "00000000-0000-4000-8000-000000000001";

    @Test
    void deniedUserCannotCreateReadOrCancelBeforeApplicationServiceAccess() throws Exception
    {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext())
        {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("aps.enabled=true", "aps.api.enabled=true", "aps.persistence.enabled=true")
                    .applyTo(context);
            context.register(TestWebSecurity.class);
            context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mockMvc.perform(post("/api/aps/v1/plan-requests").with(user("outsider"))
                            .header("Idempotency-Key", REQUEST_ID).contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/aps/v1/plan-requests/{requestId}", REQUEST_ID).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/aps/v1/plan-requests/{requestId}/events", REQUEST_ID).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/aps/v1/plan-requests/{requestId}/cancel", REQUEST_ID)
                            .with(user("outsider")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(context.getBean(PlanRequestService.class));
        }
    }

    private String validBody()
    {
        return """
                {"siteCode":"SITE_01","workshopIds":["00000000-0000-4000-8000-000000000002"],
                "orderIds":["00000000-0000-4000-8000-000000000003"],"capturedAt":"2026-09-14T00:00:00Z",
                "definitionRevision":1,"executionRevision":1,
                "horizon":{"startAt":"2026-09-14T00:00:00Z","detailEndAt":"2026-09-15T00:00:00Z",
                "endAt":"2026-09-16T00:00:00Z","planningAnchorAt":"2026-09-14T00:00:00Z",
                "timeUnitSeconds":60,"displayTimeZone":"Asia/Shanghai"},
                "parameters":{"maxSolveSeconds":30,"randomSeed":1,"solverSearchThreads":1,
                "absoluteGapLimit":0,"relativeGapLimit":0}}
                """;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import(ApsPlanRequestController.class)
    static class TestWebSecurity
    {
        @Bean(name = "ss")
        ApsSystemAuthorizationTest.DenyPermissionService denyPermissionService()
        {
            return new ApsSystemAuthorizationTest.DenyPermissionService();
        }

        @Bean PlanRequestService planRequestService() { return mock(PlanRequestService.class); }
        @Bean ApsPlanProgressStream planProgressStream(PlanRequestService service)
        {
            return new ApsPlanProgressStream(service);
        }
        @Bean ApsAuditActorProvider actorProvider() { return () -> new ApsAuditActor("300", "outsider"); }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception
        {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }
    }
}
