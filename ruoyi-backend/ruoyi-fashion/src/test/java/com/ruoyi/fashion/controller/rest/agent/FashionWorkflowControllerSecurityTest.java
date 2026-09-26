package com.ruoyi.fashion.controller.rest.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.fashion.application.agent.FashionAgentService;
import com.ruoyi.fashion.application.agent.run.FashionRequirementRunService;
import com.ruoyi.fashion.application.customer.FashionCustomerService;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.controller.rest.customer.FashionCustomerController;
import com.ruoyi.fashion.controller.rest.quote.FashionQuoteDraftController;
import com.ruoyi.fashion.controller.rest.quote.FashionSelectionController;
import com.ruoyi.fashion.application.selection.FashionSelectionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class FashionWorkflowControllerSecurityTest {
    @Test
    void workflowEndpointsDeclareGranularPermissionsAndDoNotAuditSensitiveBodies() throws Exception {
        assertPermission(FashionCustomerController.class.getMethod("list", String.class, String.class,
                int.class, int.class), "fashion:customer:list", false);
        assertPermission(FashionQuoteDraftController.class.getMethod("create",
                com.ruoyi.fashion.controller.rest.quote.QuoteDraftRequest.class), "fashion:quote:add", true);
        assertPermission(FashionAgentController.class.getMethod("publish", String.class, String.class,
                AgentVersionPublishRequest.class), "fashion:ai:agent:publish", true);
        assertPermission(FashionRequirementRunController.class.getMethod("create", RequirementRunCreateRequest.class),
                "fashion:ai:run:execute", true);
        assertPermission(FashionRequirementRunController.class.getMethod(
                "createProductAttribute", ProductAttributeRunCreateRequest.class),
                "fashion:ai:run:execute", true);
        assertPermission(FashionRequirementRunController.class.getMethod("cancel", String.class, RunCancelRequest.class),
                "fashion:ai:run:cancel", true);
        assertPermission(FashionRequirementRunController.class.getMethod("apply", String.class, RequirementApplyRequest.class),
                "fashion:ai:run:apply", true);
        assertPermission(FashionRequirementRunController.class.getMethod(
                "applyProductAttributes", String.class, ProductAttributeApplyRequest.class),
                "fashion:ai:run:apply", true);
        assertPermission(FashionRequirementRunController.class.getMethod(
                "createSelection", SelectionRunCreateRequest.class), "fashion:ai:run:execute", true);
        assertPermission(FashionRequirementRunController.class.getMethod(
                "applySelection", String.class, SelectionApplyRequest.class), "fashion:ai:run:apply", true);
        assertEquals(false, FashionRequirementRunController.class.getMethod("create", RequirementRunCreateRequest.class)
                .getAnnotation(Log.class).isSaveRequestData());
    }

    @Test
    void workflowPagesRejectAnonymousAndUsersWithoutPermission() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(DenyAllWebSecurity.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mvc.perform(get("/fashion/customers")).andExpect(status().isUnauthorized());
            mvc.perform(get("/fashion/customers").with(user("operator"))).andExpect(status().isForbidden());
            mvc.perform(get("/fashion/quotes").with(user("operator"))).andExpect(status().isForbidden());
            mvc.perform(get("/fashion/ai/agents").with(user("operator"))).andExpect(status().isForbidden());
            mvc.perform(get("/fashion/ai/runs/capabilities/requirement-analysis").with(user("operator")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/fashion/ai/runs/capabilities/product-attribute-suggestion").with(user("operator")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/fashion/ai/runs/capabilities/selection-styling").with(user("operator")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/fashion/quotes/1/selection").with(user("operator")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/fashion/ai/runs/by-correlation/run-950007").with(user("operator")))
                    .andExpect(status().isForbidden());
        }
    }

    private static void assertPermission(Method method, String permission, boolean audited) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('" + permission + "')", authorization.value());
        if (audited) assertNotNull(method.getAnnotation(Log.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({FashionCustomerController.class, FashionQuoteDraftController.class,
            FashionSelectionController.class, FashionAgentController.class, FashionRequirementRunController.class})
    static class DenyAllWebSecurity {
        @Bean(name = "ss") DenyPermissionService denyPermissionService() { return new DenyPermissionService(); }
        @Bean FashionCustomerService customerService() { return mock(FashionCustomerService.class); }
        @Bean FashionQuoteDraftService quoteService() { return mock(FashionQuoteDraftService.class); }
        @Bean FashionAgentService agentService() { return mock(FashionAgentService.class); }
        @Bean FashionRequirementRunService runService() { return mock(FashionRequirementRunService.class); }
        @Bean FashionSelectionService selectionService() { return mock(FashionSelectionService.class); }
        @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(r -> r.anyRequest().authenticated()).build();
        }
    }

    static class DenyPermissionService { public boolean hasPermi(String permission) { return false; } }
}
