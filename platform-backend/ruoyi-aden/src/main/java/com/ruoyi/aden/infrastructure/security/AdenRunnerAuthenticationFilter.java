package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.api.common.AdenApiErrorWriter;
import com.ruoyi.aden.application.runner.AdenRunnerAuthenticationPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runner 路径严格选择一种 token scheme，不向 RuoYi JWT 或另一类 Runner token 降级。 */
public final class AdenRunnerAuthenticationFilter extends OncePerRequestFilter {
    static final String CREDENTIAL_SCHEME = "AdenCredential ";
    static final String SESSION_SCHEME = "AdenRunner ";
    private static final String SESSION_EXCHANGE = "/api/v1/aden/runner/v1/sessions";

    private final AdenRunnerAuthenticationPort authentication;
    private final AdenApiErrorWriter writer;
    private final Clock clock;

    public AdenRunnerAuthenticationFilter(AdenRunnerAuthenticationPort authentication,
                                          AdenApiErrorWriter writer, Clock clock) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean credentialExpected = "POST".equals(request.getMethod())
                && SESSION_EXCHANGE.equals(request.getRequestURI());
        String prefix = credentialExpected ? CREDENTIAL_SCHEME : SESSION_SCHEME;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(prefix) || header.length() == prefix.length()) {
            writer.runnerUnauthorized(request, response);
            return;
        }
        String token = header.substring(prefix.length());
        Optional<?> principal = credentialExpected
                ? authentication.authenticateCredential(token, clock.instant())
                : authentication.authenticateSession(token, clock.instant());
        if (principal.isEmpty()) {
            writer.runnerUnauthorized(request, response);
            return;
        }
        var authenticationToken = UsernamePasswordAuthenticationToken.authenticated(
                principal.get(), null, List.of(new SimpleGrantedAuthority("ROLE_ADEN_RUNNER")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationToken);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
