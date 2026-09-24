package com.ruoyi.fashion.controller.rest.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.fashion.application.image.FashionQuoteImageService;
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

class FashionQuoteImageControllerSecurityTest {
    @Test
    void imageEndpointsUseSeparatedListCreateAndReviewPermissions() throws Exception {
        assertPermission(FashionQuoteImageController.class.getMethod("workspace", String.class),
                "fashion:image:list", false);
        assertPermission(FashionQuoteImageController.class.getMethod("create", String.class,
                QuoteImageCreateRequest.class), "fashion:image:create", true);
        assertPermission(FashionQuoteImageController.class.getMethod("review", String.class, String.class,
                QuoteImageReviewRequest.class), "fashion:image:review", true);
        assertPermission(FashionQuoteImageController.class.getMethod("adopt", String.class, String.class,
                int.class, long.class), "fashion:image:review", true);
        assertPermission(FashionQuoteImageController.class.getMethod("content", String.class, String.class,
                int.class), "fashion:image:list", false);
    }

    @Test
    void anonymousAndUnprivilegedUsersCannotReadPrivateImageResults() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(DenyAllSecurity.class); context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String path = "/fashion/quotes/100/images/200/results/1/content";
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).with(user("operator"))).andExpect(status().isForbidden());
        }
    }

    private static void assertPermission(Method method, String permission, boolean audited) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization); assertEquals("@ss.hasPermi('" + permission + "')", authorization.value());
        if (audited) assertNotNull(method.getAnnotation(Log.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import(FashionQuoteImageController.class)
    static class DenyAllSecurity {
        @Bean(name = "ss") DenyPermissionService denyPermissionService() { return new DenyPermissionService(); }
        @Bean FashionQuoteImageService imageService() { return mock(FashionQuoteImageService.class); }
        @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(value -> value.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(value -> value.anyRequest().authenticated()).build();
        }
    }

    static class DenyPermissionService { public boolean hasPermi(String permission) { return false; } }
}
