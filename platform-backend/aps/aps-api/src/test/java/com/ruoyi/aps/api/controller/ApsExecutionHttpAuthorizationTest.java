package com.ruoyi.aps.api.controller;

import com.ruoyi.aps.application.execution.ExecutionLifecycleService;
import com.ruoyi.aps.application.execution.ProductionReportingService;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApsExecutionHttpAuthorizationTest
{
    private static final String RUN_ID = "00000000-0000-4000-8000-000000000091";

    @Test
    void deniedUserCannotReachExecutionApplicationService() throws Exception
    {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext())
        {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("aps.enabled=true", "aps.api.enabled=true", "aps.persistence.enabled=true")
                    .applyTo(context);
            context.register(TestWebSecurity.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mvc.perform(get("/api/aps/v1/execution-runs/{id}", RUN_ID).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/execution-runs")
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000092")
                            .contentType("application/json").content("""
                                    {"planVersionId":"00000000-0000-4000-8000-000000000093",
                                     "planJobId":"00000000-0000-4000-8000-000000000094",
                                     "assignedQty":10,"uomCode":"PCS"}
                                    """).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/execution-runs/{id}/transitions", RUN_ID)
                            .contentType("application/json").content("""
                                    {"action":"START","expectedRowVersion":0,
                                     "occurredAt":"2026-09-14T00:01:00Z"}
                                    """).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/execution-runs/{id}/resource-changes", RUN_ID)
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000095")
                            .contentType("application/json").content("""
                                    {"expectedRowVersion":1,
                                     "replacedResourceId":"00000000-0000-4000-8000-000000000096",
                                     "resourceId":"00000000-0000-4000-8000-000000000097",
                                     "activityType":"RUN","occurredAt":"2026-09-14T00:02:00Z"}
                                    """).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/execution-runs/{id}/phase-advances", RUN_ID)
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000106")
                            .contentType("application/json").content("""
                                    {"expectedRowVersion":1,
                                     "occurredAt":"2026-09-14T00:02:30Z",
                                     "reason":"进入下一阶段"}
                                    """).with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/execution-runs/{id}/reports", RUN_ID)
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000098")
                            .contentType("application/json").content("""
                                    {"expectedRowVersion":1,
                                     "planJobMemberId":"00000000-0000-4000-8000-000000000101",
                                     "taskId":"00000000-0000-4000-8000-000000000102",
                                     "reportType":"PROGRESS","reportedAt":"2026-09-14T00:03:00Z",
                                     "quantities":{"processedQty":1,"goodQty":1,"pendingQty":0,
                                       "rejectedQty":0,"scrapQty":0,"transferredQty":0},"uomCode":"PCS"}
                                    """)
                            .with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/production-reports/{id}/corrections", RUN_ID)
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000099")
                            .contentType("application/json").content("""
                                    {"expectedRunRowVersion":2,"expectedReportRowVersion":0,
                                     "reportedAt":"2026-09-14T00:04:00Z",
                                     "quantities":{"processedQty":1,"goodQty":1,"pendingQty":0,
                                       "rejectedQty":0,"scrapQty":0,"transferredQty":0},
                                     "reason":"分类更正"}
                                    """)
                            .with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/output-lots/{id}/quality-decisions", RUN_ID)
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000100")
                            .contentType("application/json").content("""
                                    {"expectedRunRowVersion":3,"expectedOutputLotRowVersion":0,
                                     "decision":"RELEASE","quantity":1,
                                     "occurredAt":"2026-09-14T00:05:00Z","reason":"质检放行"}
                                    """)
                            .with(user("outsider")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/aps/v1/output-lots/{id}/quantity-movements", RUN_ID)
                            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000103")
                            .contentType("application/json").content("""
                                    {"expectedRunRowVersion":4,"expectedOutputLotRowVersion":1,
                                     "materialDemandId":"00000000-0000-4000-8000-000000000104",
                                     "targetTaskId":"00000000-0000-4000-8000-000000000105",
                                     "operation":"RESERVE","quantity":1,
                                     "occurredAt":"2026-09-14T00:06:00Z"}
                                    """)
                            .with(user("outsider")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(context.getBean(ExecutionLifecycleService.class));
            verifyNoInteractions(context.getBean(ProductionReportingService.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({ApsExecutionController.class, ApsProductionReportingController.class})
    static class TestWebSecurity
    {
        @Bean(name = "ss") ApsSystemAuthorizationTest.DenyPermissionService permissions()
        {
            return new ApsSystemAuthorizationTest.DenyPermissionService();
        }
        @Bean ExecutionLifecycleService service() { return mock(ExecutionLifecycleService.class); }
        @Bean ProductionReportingService reportingService() { return mock(ProductionReportingService.class); }
        @Bean ApsAuditActorProvider actorProvider() { return () -> new ApsAuditActor("300", "outsider"); }
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception
        {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }
    }
}
