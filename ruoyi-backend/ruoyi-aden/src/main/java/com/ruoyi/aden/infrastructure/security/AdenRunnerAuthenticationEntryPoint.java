package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.api.common.AdenApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Objects;

public final class AdenRunnerAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AdenApiErrorWriter writer;

    public AdenRunnerAuthenticationEntryPoint(AdenApiErrorWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        writer.runnerUnauthorized(request, response);
    }
}
