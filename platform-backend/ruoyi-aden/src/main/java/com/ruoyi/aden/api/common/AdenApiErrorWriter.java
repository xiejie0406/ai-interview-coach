package com.ruoyi.aden.api.common;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** 在 MVC 之前发生的安全拒绝也使用 Aden 的真实 HTTP 状态与错误契约。 */
public final class AdenApiErrorWriter {
    private final ObjectMapper objectMapper;

    public AdenApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "ADEN_AUTH_REQUIRED", "请先登录后再继续");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "ADEN_PERMISSION_DENIED", "当前账号无权执行此操作");
    }

    public void runnerUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED,
                "ADEN_RUNNER_AUTH_INVALID", "Runner 凭据或会话无效");
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String errorCode, String message) throws IOException {
        if (response.isCommitted()) return;
        String correlationId = AdenCorrelationIdFilter.correlationId(request);
        response.reset();
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(AdenCorrelationIdFilter.HEADER, correlationId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getWriter(), new AdenErrorEnvelope(
                status.value(), message, null, errorCode, false, correlationId, Map.of()));
        response.flushBuffer();
    }
}
