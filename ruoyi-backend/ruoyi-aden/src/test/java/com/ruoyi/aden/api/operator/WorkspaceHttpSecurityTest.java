package com.ruoyi.aden.api.operator;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.api.common.AdenApiErrorWriter;
import com.ruoyi.aden.api.common.AdenApiExceptionHandler;
import com.ruoyi.aden.api.common.AdenCorrelationIdFilter;
import com.ruoyi.aden.api.common.AdenOriginGuardFilter;
import com.ruoyi.aden.api.common.AdenNotFoundController;
import com.ruoyi.aden.application.security.AdenOperatorPrincipalProvider;
import com.ruoyi.aden.application.workspace.AdenWorkspaceService;
import com.ruoyi.aden.configuration.AdenRuntimeConfiguration;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.workspace.AdenWorkspace;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceForbiddenAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import com.ruoyi.aden.infrastructure.security.RuoYiAdenOperatorPrincipalProvider;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig
@ContextConfiguration(classes = WorkspaceHttpSecurityTest.TestConfiguration.class)
@WebAppConfiguration
class WorkspaceHttpSecurityTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AdenApiErrorWriter errorWriter;

    private MockMvc mockMvc;

    @BeforeEach
    void createMockMvc() {
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new AdenCorrelationIdFilter(), new AdenOriginGuardFilter(errorWriter))
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousRequestGetsReal401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/aden/workspaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.errorCode").value("ADEN_AUTH_REQUIRED"))
                .andExpect(header().exists(AdenCorrelationIdFilter.HEADER));
    }

    @Test
    void authenticatedRequestWithoutFeaturePermissionGetsReal403() throws Exception {
        mockMvc.perform(get("/api/v1/aden/workspaces").with(authentication(loginAuthentication(Set.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value("ADEN_PERMISSION_DENIED"));
    }

    @Test
    void listPermissionReturnsOnlyMembershipBoundRepositoryResult() throws Exception {
        mockMvc.perform(get("/api/v1/aden/workspaces")
                        .with(authentication(loginAuthentication(Set.of(AdenWorkspaceService.LIST_PERMISSION)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void wildcardPermissionStillReturnsNoWorkspaceWithoutMembership() throws Exception {
        mockMvc.perform(get("/api/v1/aden/workspaces")
                        .with(authentication(loginAuthentication(Set.of("*:*:*")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void validCreateReturns201LocationAndStrongEtag() throws Exception {
        mockMvc.perform(post("/api/v1/aden/admin/workspaces")
                        .with(authentication(loginAuthentication(Set.of(AdenWorkspaceService.CREATE_PERMISSION))))
                        .contentType("application/json")
                        .content("{\"displayName\":\"HTTP 工作区\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/aden/workspaces/11111111-1111-4111-8111-111111111111"))
                .andExpect(header().string("ETag",
                        "\"workspace-11111111-1111-4111-8111-111111111111-v0\""))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.version").value("0"));
    }

    @Test
    void invalidBodyGetsReal400InsteadOfRuoYiHttp200Error() throws Exception {
        mockMvc.perform(post("/api/v1/aden/admin/workspaces")
                        .with(authentication(loginAuthentication(Set.of(AdenWorkspaceService.CREATE_PERMISSION))))
                        .contentType("application/json")
                        .content("{\"displayName\":\"\\n\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("ADEN_INVALID_ARGUMENT"));
    }

    @Test
    void originPreflightIs403WithoutWildcardCorsHeader() throws Exception {
        mockMvc.perform(options("/api/v1/aden/workspaces")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(jsonPath("$.errorCode").value("ADEN_PERMISSION_DENIED"));
    }

    @Test
    void ordinaryOriginRequestIsAlso403BeforeController() throws Exception {
        mockMvc.perform(get("/api/v1/aden/workspaces")
                        .header("Origin", "https://attacker.example")
                        .with(authentication(loginAuthentication(Set.of(AdenWorkspaceService.LIST_PERMISSION)))))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(jsonPath("$.errorCode").value("ADEN_PERMISSION_DENIED"));
    }

    @Test
    void unknownAdenResourceGetsReal404Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/aden/unknown")
                        .with(authentication(loginAuthentication(Set.of("*:*:*")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.errorCode").value("ADEN_TASK_NOT_FOUND"));
    }

    private static org.springframework.security.authentication.UsernamePasswordAuthenticationToken loginAuthentication(
            Set<String> permissions) {
        SysUser user = new SysUser();
        user.setUserName("http-operator");
        LoginUser loginUser = new LoginUser(42L, 7L, user, permissions);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                loginUser, null, loginUser.getAuthorities());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AdenApiErrorWriter adenApiErrorWriter(ObjectMapper objectMapper) {
            return new AdenApiErrorWriter(objectMapper);
        }

        @Bean
        AdenApiExceptionHandler adenApiExceptionHandler() {
            return new AdenApiExceptionHandler();
        }

        @Bean
        AdenNotFoundController adenNotFoundController() {
            return new AdenNotFoundController();
        }

        @Bean
        AdenWorkspaceRepository adenWorkspaceRepository() {
            return new EmptyWorkspaceRepository();
        }

        @Bean
        AdenIdGenerator adenIdGenerator() {
            List<String> ids = new ArrayList<>(List.of(
                    "11111111-1111-4111-8111-111111111111",
                    "22222222-2222-4222-8222-222222222222"));
            return () -> ids.remove(0);
        }

        @Bean
        AdenWorkspaceService adenWorkspaceService(AdenWorkspaceRepository repository,
                                                   AdenIdGenerator idGenerator) {
            return new AdenWorkspaceService(repository, idGenerator,
                    Clock.fixed(Instant.parse("2026-09-13T02:00:00Z"), ZoneOffset.UTC));
        }

        @Bean
        AdenOperatorPrincipalProvider adenOperatorPrincipalProvider() {
            return new RuoYiAdenOperatorPrincipalProvider();
        }

        @Bean
        WorkspaceController workspaceController(AdenOperatorPrincipalProvider provider,
                                                AdenWorkspaceService service) {
            return new WorkspaceController(provider, service);
        }

        @Bean(name = "ss")
        TestPermissionService permissionService() {
            return new TestPermissionService();
        }

        @Bean
        OncePerRequestFilter jwtAuthenticationTokenFilter() {
            return new PassThroughJwtFilter();
        }

        @Bean
        @Order(2)
        SecurityFilterChain adenChain(HttpSecurity http,
                                      @org.springframework.beans.factory.annotation.Qualifier("jwtAuthenticationTokenFilter")
                                      OncePerRequestFilter jwt,
                                      AdenApiErrorWriter writer) throws Exception {
            return new AdenRuntimeConfiguration().adenOperatorSecurityFilterChain(http, jwt, writer);
        }
    }

    public static final class TestPermissionService {
        public boolean hasPermi(String permission) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return principal instanceof LoginUser loginUser
                    && (loginUser.getPermissions().contains("*:*:*")
                    || loginUser.getPermissions().contains(permission));
        }
    }

    private static final class PassThroughJwtFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            chain.doFilter(request, response);
        }
    }

    private static final class EmptyWorkspaceRepository implements AdenWorkspaceRepository {
        private final AtomicInteger writes = new AtomicInteger();

        @Override public List<AdenWorkspaceMembership> findActiveByUserId(long userId, int limit) { return List.of(); }
        @Override public Optional<AdenWorkspaceMembership> findActiveMembership(AdenWorkspaceId id, long userId) {
            return Optional.empty();
        }
        @Override public void insertWorkspace(AdenWorkspace workspace) { writes.incrementAndGet(); }
        @Override public void insertInitialOwner(AdenWorkspaceMembership membership) { writes.incrementAndGet(); }
        @Override public void insertAudit(AdenWorkspaceAudit audit) { writes.incrementAndGet(); }
        @Override public void insertWorkspaceForbiddenAudit(AdenWorkspaceForbiddenAudit audit) {
            writes.incrementAndGet();
        }
    }
}
