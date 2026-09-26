package com.ruoyi.fashion.infrastructure.airuntime.security;

import java.time.Instant;

/** 已通过进程级服务认证的对端身份；它不代表用户、客户或 Tool 授权。 */
public record FashionServicePrincipal(
        String serviceId,
        String keyId,
        String audience,
        Instant signedAt,
        String nonce) {
}
