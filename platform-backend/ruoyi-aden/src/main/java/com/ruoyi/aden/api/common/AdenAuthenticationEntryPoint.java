package com.ruoyi.aden.api.common;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Objects;

public final class AdenAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AdenApiErrorWriter writer;

    public AdenAuthenticationEntryPoint(AdenApiErrorWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        writer.unauthorized(request, response);
    }
}
