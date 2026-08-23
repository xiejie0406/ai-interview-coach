package com.aiinterviewcoach.adapters.inbound.security;

import com.aiinterviewcoach.adapters.inbound.rest.common.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/** Login/Register 不依赖既有 Session CSRF token，但仍严格拒绝跨站浏览器提交。 */
public final class SameOriginAuthMutationFilter extends OncePerRequestFilter {

    private static final Set<String> PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");
    private final ApiErrorResponseWriter errorWriter;

    public SameOriginAuthMutationFilter(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = java.util.Objects.requireNonNull(errorWriter);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!sameOrigin(request)) {
            errorWriter.forbidden(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean sameOrigin(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !fetchSite.equalsIgnoreCase("same-origin")) {
            return false;
        }
        String expected = requestOrigin(request);
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return normalizeOrigin(origin).map(expected::equals).orElse(false);
        }
        String referer = request.getHeader("Referer");
        return referer != null && normalizeOrigin(referer).map(expected::equals).orElse(false);
    }

    private String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = request.getServerName().toLowerCase(java.util.Locale.ROOT);
        int port = request.getServerPort();
        boolean defaultPort = (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private java.util.Optional<String> normalizeOrigin(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
                return java.util.Optional.empty();
            }
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return java.util.Optional.empty();
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443)
                    || (scheme.equals("http") && port == 80);
            return java.util.Optional.of(scheme + "://"
                    + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                    + (defaultPort ? "" : ":" + port));
        } catch (URISyntaxException exception) {
            return java.util.Optional.empty();
        }
    }
}
