package com.ruoyi.fashion.controller.rest.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.ruoyi.fashion.application.settings.FashionSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.http.HttpStatus;
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

class FashionSettingsControllerSecurityTest {
    @Test
    void readAndWriteEndpointsHaveIndependentServerPermissions() throws Exception {
        Method list = FashionSettingsController.class.getMethod("list");
        Method update = FashionSettingsController.class.getMethod("update", FashionSettingUpdateRequest.class);

        PreAuthorize listAuthorization = list.getAnnotation(PreAuthorize.class);
        PreAuthorize updateAuthorization = update.getAnnotation(PreAuthorize.class);
        assertNotNull(listAuthorization);
        assertNotNull(updateAuthorization);
        assertEquals("@ss.hasPermi('fashion:settings:list')", listAuthorization.value());
        assertEquals("@ss.hasPermi('fashion:settings:edit')", updateAuthorization.value());
    }

    @Test
    void updatePayloadRejectsUnknownFieldsEvenWhenGlobalJacksonIsLenient() {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        assertThrows(Exception.class, () -> mapper.readValue("""
                {"key":"fashion.stock.freshnessHours","value":"24","secret":"not-allowed"}
                """, FashionSettingUpdateRequest.class));
    }

    @Test
    void unauthenticatedAndUnauthorizedRequestsAreRejectedByTheRealSecurityChain() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(DenyAllWebSecurity.class);
            context.refresh();

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();

            mockMvc.perform(get("/fashion/settings"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/fashion/settings").with(user("operator")))
                    .andExpect(status().isForbidden());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import(FashionSettingsController.class)
    static class DenyAllWebSecurity {
        @Bean(name = "ss")
        DenyPermissionService denyPermissionService() {
            return new DenyPermissionService();
        }

        @Bean
        FashionSettingsService settingsService() {
            return mock(FashionSettingsService.class);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
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
