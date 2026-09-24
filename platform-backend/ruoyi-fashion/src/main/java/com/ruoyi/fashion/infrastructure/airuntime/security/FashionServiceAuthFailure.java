package com.ruoyi.fashion.infrastructure.airuntime.security;

/**
 * 稳定的服务认证失败分类。认证错误映射为 401，已认证但未授权映射为 403，
 * 防重放存储容量保护映射为 429。
 */
public enum FashionServiceAuthFailure {
    NOT_CONFIGURED("FASHION_SERVICE_AUTH_NOT_CONFIGURED"),
    MISSING_HEADER("FASHION_SERVICE_AUTH_MISSING_HEADER"),
    MALFORMED_HEADER("FASHION_SERVICE_AUTH_MALFORMED_HEADER"),
    UNAUTHORIZED_SERVICE("FASHION_SERVICE_AUTH_UNAUTHORIZED_SERVICE"),
    UNKNOWN_KEY("FASHION_SERVICE_AUTH_UNKNOWN_KEY"),
    EXPIRED("FASHION_SERVICE_AUTH_EXPIRED"),
    BODY_DIGEST_MISMATCH("FASHION_SERVICE_AUTH_BODY_DIGEST_MISMATCH"),
    BAD_SIGNATURE("FASHION_SERVICE_AUTH_BAD_SIGNATURE"),
    REPLAYED_NONCE("FASHION_SERVICE_AUTH_REPLAYED_NONCE"),
    CAPACITY_EXCEEDED("FASHION_SERVICE_AUTH_CAPACITY_EXCEEDED");

    private final String errorCode;

    FashionServiceAuthFailure(String errorCode) {
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
