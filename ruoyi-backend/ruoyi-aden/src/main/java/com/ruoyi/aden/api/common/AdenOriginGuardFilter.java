package com.ruoyi.aden.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** 当前 Aden Operator API 只允许无 Origin 的 Electron main / 非浏览器客户端。 */
public final class AdenOriginGuardFilter extends OncePerRequestFilter {
    private final AdenApiErrorWriter errorWriter;

    public AdenOriginGuardFilter(AdenApiErrorWriter errorWriter) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/aden/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader("Origin") != null) {
            errorWriter.forbidden(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
