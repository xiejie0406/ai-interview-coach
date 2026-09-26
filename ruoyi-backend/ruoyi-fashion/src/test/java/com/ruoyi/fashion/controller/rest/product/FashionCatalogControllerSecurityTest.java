package com.ruoyi.fashion.controller.rest.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.fashion.application.importing.FashionProductImportService;
import com.ruoyi.fashion.application.importing.FashionCatalogValueImportService;
import com.ruoyi.fashion.application.material.FashionProductMaterialService;
import com.ruoyi.fashion.application.product.FashionProductService;
import com.ruoyi.fashion.controller.rest.importing.FashionProductImportController;
import com.ruoyi.fashion.controller.rest.importing.FashionCatalogValueImportController;
import com.ruoyi.fashion.controller.rest.importing.CatalogRestoreRequest;
import com.ruoyi.fashion.controller.rest.importing.ProductImportPublishRequest;
import com.ruoyi.fashion.controller.rest.material.FashionProductMaterialController;
import com.ruoyi.fashion.controller.rest.material.MaterialConfirmRequest;
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

class FashionCatalogControllerSecurityTest {
    @Test
    void endpointsDeclareIndependentPermissionsAndWriteAudit() throws Exception {
        assertPermission(FashionProductController.class.getMethod("list", String.class, String.class,
                String.class, String.class, int.class, int.class), "fashion:product:list", false);
        assertPermission(FashionProductController.class.getMethod("detail", String.class),
                "fashion:product:query", false);
        assertPermission(FashionProductController.class.getMethod("create", ProductCreateRequest.class),
                "fashion:product:edit", true);
        assertPermission(FashionProductController.class.getMethod("update", ProductBulkUpdateRequest.class),
                "fashion:product:edit", true);
        assertPermission(FashionProductController.class.getMethod(
                "status", String.class, ProductStatusRequest.class), "fashion:product:edit", true);
        assertPermission(FashionProductImportController.class.getMethod(
                "publish", String.class, ProductImportPublishRequest.class), "fashion:product:import", true);
        assertPermission(FashionProductMaterialController.class.getMethod(
                "confirm", String.class, MaterialConfirmRequest.class), "fashion:product:image", true);
        assertPermission(FashionCatalogValueImportController.class.getMethod(
                "publishPrice", String.class, ProductImportPublishRequest.class), "fashion:price:import", true);
        assertPermission(FashionCatalogValueImportController.class.getMethod(
                "publishStock", String.class, ProductImportPublishRequest.class), "fashion:stock:import", true);
        Method restore = FashionCatalogValueImportController.class.getMethod(
                "restoreStock", String.class, CatalogRestoreRequest.class);
        assertEquals("@ss.hasPermi('fashion:stock:import') and @ss.hasPermi('fashion:import:restore')",
                restore.getAnnotation(PreAuthorize.class).value());
        assertNotNull(restore.getAnnotation(Log.class));
    }

    @Test
    void catalogRequestsRejectAnonymousAndUsersWithoutPermission() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(DenyAllWebSecurity.class);
            context.refresh();

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mockMvc.perform(get("/fashion/products")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/fashion/products").with(user("operator"))).andExpect(status().isForbidden());
            mockMvc.perform(get("/fashion/imports/products/template").with(user("operator")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/fashion/materials/123").with(user("operator")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/fashion/imports/prices/template").with(user("operator")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/fashion/imports/stocks/template").with(user("operator")))
                    .andExpect(status().isForbidden());
        }
    }

    private static void assertPermission(Method method, String permission, boolean audited) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('" + permission + "')", authorization.value());
        if (audited) {
            assertNotNull(method.getAnnotation(Log.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({FashionProductController.class, FashionProductImportController.class,
            FashionProductMaterialController.class, FashionCatalogValueImportController.class})
    static class DenyAllWebSecurity {
        @Bean(name = "ss")
        DenyPermissionService denyPermissionService() {
            return new DenyPermissionService();
        }

        @Bean
        FashionProductService productService() {
            return mock(FashionProductService.class);
        }

        @Bean
        FashionProductImportService productImportService() {
            return mock(FashionProductImportService.class);
        }

        @Bean
        FashionProductMaterialService materialService() {
            return mock(FashionProductMaterialService.class);
        }

        @Bean
        FashionCatalogValueImportService catalogValueImportService() {
            return mock(FashionCatalogValueImportService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .build();
        }
    }

    static class DenyPermissionService {
        public boolean hasPermi(String permission) {
            return false;
        }
    }
}
