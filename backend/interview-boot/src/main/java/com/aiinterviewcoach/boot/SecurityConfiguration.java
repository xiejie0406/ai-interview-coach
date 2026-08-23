package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.inbound.rest.common.ApiErrorResponseWriter;
import com.aiinterviewcoach.adapters.inbound.security.CsrfCookieMaterializationFilter;
import com.aiinterviewcoach.adapters.inbound.security.SameOriginAuthMutationFilter;
import com.aiinterviewcoach.boot.properties.ConfigurationSafetyGuard;
import com.aiinterviewcoach.boot.properties.SecurityBaselineProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(prefix = "legacy.ai-backend", name = "enabled", havingValue = "true")
public class SecurityConfiguration {
    @Bean
    ConfigurationSafetyGuard configurationSafetyGuard(Environment environment) {
        return new ConfigurationSafetyGuard(environment);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityBaselineProperties securityProperties,
            ApiErrorResponseWriter errorResponseWriter) throws Exception {
        // 只有 double-submit CSRF token Cookie 可由前端读取；认证 Session Cookie 仍强制 HttpOnly。
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("AIC-XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-AIC-XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .secure(securityProperties.isSecureCookies())
                .sameSite(securityProperties.getSameSite()));
        // SPA 从 double-submit Cookie 读取原始 token 并通过 header 回传；显式使用非 XOR
        // handler，避免 Spring Security 6 默认的 BREACH 编码格式与 Cookie 值不一致。
        CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register"))
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), geolocation=(), microphone=(self)")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponseWriter.unauthorized(request, response))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof CsrfException) {
                                errorResponseWriter.csrfRejected(request, response);
                            } else {
                                errorResponseWriter.forbidden(request, response);
                            }
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies/current").permitAll()
                        // 公开资源即使尚未启用，也不能落入匿名认证入口并被误报为 AUTH_REQUIRED。
                        // 能力是否存在由实际 Controller/统一错误契约决定；会话只由 /me 判定。
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/questions", "/api/v1/questions/**",
                                "/api/v1/plans", "/api/v1/plans/**",
                                "/api/v1/status", "/api/v1/status/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/questions").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/questions/*/my-answer").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me")
                        .permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/me")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .permitAll()
                        // 业务控制器从 AIC_SESSION 解析租户和用户；Spring Security 不持有第二套登录态。
                        .requestMatchers("/api/v1/interview-plans", "/api/v1/interview-plans/**",
                                "/api/v1/interviews", "/api/v1/interviews/**")
                        .permitAll()
                        .requestMatchers("/api/v1/transcripts/**", "/api/v1/audio-artifacts/**",
                                "/api/v1/consents/**", "/ws/v1/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(new SameOriginAuthMutationFilter(errorResponseWriter), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieMaterializationFilter(), CsrfFilter.class);
        return http.build();
    }
}
