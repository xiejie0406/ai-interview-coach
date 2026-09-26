package com.ruoyi.fashion.infrastructure.airuntime.security;

import java.util.Locale;
import java.util.Objects;

/** 待签名或校验的原始 HTTP 请求要素；首期契约明确禁止 query，避免代理层规范化歧义。 */
public record FashionServiceRequest(String method, String rawPath, byte[] body) {

    public FashionServiceRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rawPath, "rawPath");
        Objects.requireNonNull(body, "body");
        method = method.strip().toUpperCase(Locale.ROOT);
        if (!method.matches("[A-Z]+")) {
            throw new IllegalArgumentException("method 必须是大写 HTTP token");
        }
        if (!rawPath.startsWith("/") || rawPath.contains("?") || rawPath.contains("#")
                || rawPath.contains("\r") || rawPath.contains("\n")) {
            throw new IllegalArgumentException("rawPath 必须是无 query/fragment 的绝对路径");
        }
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
