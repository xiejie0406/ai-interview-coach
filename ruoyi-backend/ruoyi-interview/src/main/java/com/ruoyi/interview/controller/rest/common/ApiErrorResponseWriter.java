package com.ruoyi.interview.controller.rest.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 为发生在 MVC advice 之前的失败写入统一错误契约。 */
public final class ApiErrorResponseWriter {
    public void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录后再继续", false);
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "当前账号无权执行此操作", false);
    }

    public void csrfRejected(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "CSRF_REJECTED", "安全校验失败，请刷新后重试", false);
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String userMessage,
            boolean retryable) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String correlationId = CorrelationIdFilter.correlationId(request);
        response.resetBuffer();
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER, correlationId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write("{\"error\":{"
                + "\"code\":\"" + escapeJson(code) + "\","
                + "\"userMessage\":\"" + escapeJson(userMessage) + "\","
                + "\"retryable\":" + retryable + ","
                + "\"correlationId\":\"" + escapeJson(correlationId) + "\","
                + "\"details\":{}"
                + "}}");
        response.flushBuffer();
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

