package com.ruoyi.fashion.infrastructure.airuntime.security;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 签名请求头的类型化快照。 */
public record FashionServiceAuthHeaders(
        String serviceId,
        String keyId,
        String timestamp,
        String nonce,
        String audience,
        String contentSha256,
        String signature) {

    public FashionServiceAuthHeaders {
        serviceId = required(serviceId);
        keyId = required(keyId);
        timestamp = required(timestamp);
        nonce = required(nonce);
        audience = required(audience);
        contentSha256 = required(contentSha256);
        signature = required(signature);
    }

    public static FashionServiceAuthHeaders from(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers");
        Map<String, String> normalized = new LinkedHashMap<>();
        try {
            headers.forEach((name, value) -> {
                if (name != null) {
                    String normalizedName = name.toLowerCase(Locale.ROOT);
                    if (normalized.containsKey(normalizedName)) {
                        throw new DuplicateHeaderException();
                    }
                    normalized.put(normalizedName, value);
                }
            });
        } catch (DuplicateHeaderException exception) {
            throw new FashionServiceAuthenticationException(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
        try {
            return new FashionServiceAuthHeaders(
                    normalized.get(FashionServiceAuthHeaderNames.SERVICE_ID.toLowerCase(Locale.ROOT)),
                    normalized.get(FashionServiceAuthHeaderNames.KEY_ID.toLowerCase(Locale.ROOT)),
                    normalized.get(FashionServiceAuthHeaderNames.TIMESTAMP.toLowerCase(Locale.ROOT)),
                    normalized.get(FashionServiceAuthHeaderNames.NONCE.toLowerCase(Locale.ROOT)),
                    normalized.get(FashionServiceAuthHeaderNames.AUDIENCE.toLowerCase(Locale.ROOT)),
                    normalized.get(FashionServiceAuthHeaderNames.CONTENT_SHA256.toLowerCase(Locale.ROOT)),
                    normalized.get(FashionServiceAuthHeaderNames.SIGNATURE.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new FashionServiceAuthenticationException(FashionServiceAuthFailure.MISSING_HEADER);
        }
    }

    private static final class DuplicateHeaderException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public Map<String, String> toMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(FashionServiceAuthHeaderNames.SERVICE_ID, serviceId);
        headers.put(FashionServiceAuthHeaderNames.KEY_ID, keyId);
        headers.put(FashionServiceAuthHeaderNames.TIMESTAMP, timestamp);
        headers.put(FashionServiceAuthHeaderNames.NONCE, nonce);
        headers.put(FashionServiceAuthHeaderNames.AUDIENCE, audience);
        headers.put(FashionServiceAuthHeaderNames.CONTENT_SHA256, contentSha256);
        headers.put(FashionServiceAuthHeaderNames.SIGNATURE, signature);
        return Map.copyOf(headers);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少服务认证请求头");
        }
        return value;
    }
}
