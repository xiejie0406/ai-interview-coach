package com.ruoyi.fashion.infrastructure.airuntime.security;

/** 不回显请求正文、签名或密钥材料的服务认证异常。 */
public final class FashionServiceAuthenticationException extends RuntimeException {

    private final FashionServiceAuthFailure failure;

    public FashionServiceAuthenticationException(FashionServiceAuthFailure failure) {
        super(failure.errorCode());
        this.failure = failure;
    }

    public FashionServiceAuthFailure failure() {
        return failure;
    }

    public int httpStatus() {
        return switch (failure) {
            case UNAUTHORIZED_SERVICE -> 403;
            case CAPACITY_EXCEEDED -> 429;
            default -> 401;
        };
    }
}
