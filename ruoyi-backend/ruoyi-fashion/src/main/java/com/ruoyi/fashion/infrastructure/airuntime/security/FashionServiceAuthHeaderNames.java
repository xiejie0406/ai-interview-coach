package com.ruoyi.fashion.infrastructure.airuntime.security;

/** Java 与 Python 内部调用使用的服务身份请求头。 */
public final class FashionServiceAuthHeaderNames {

    public static final String SERVICE_ID = "X-Fashion-Service-Id";
    public static final String KEY_ID = "X-Fashion-Key-Id";
    public static final String TIMESTAMP = "X-Fashion-Timestamp";
    public static final String NONCE = "X-Fashion-Nonce";
    public static final String AUDIENCE = "X-Fashion-Audience";
    public static final String CONTENT_SHA256 = "X-Fashion-Content-SHA256";
    public static final String SIGNATURE = "X-Fashion-Signature";

    private FashionServiceAuthHeaderNames() {
    }
}
