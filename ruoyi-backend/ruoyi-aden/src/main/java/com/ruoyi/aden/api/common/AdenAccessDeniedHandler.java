package com.ruoyi.aden.api.common;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.Objects;

public final class AdenAccessDeniedHandler implements AccessDeniedHandler {
    private final AdenApiErrorWriter writer;

    public AdenAccessDeniedHandler(AdenApiErrorWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        writer.forbidden(request, response);
    }
}
