package com.ruoyi.fashion.configuration;

import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentity;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentityKeyRing;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentityPolicy;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceReplayStore;
import com.ruoyi.fashion.infrastructure.airuntime.security.HmacSha256FashionServiceIdentity;
import com.ruoyi.fashion.infrastructure.airuntime.security.InMemoryFashionServiceReplayStore;
import com.ruoyi.fashion.infrastructure.airuntime.security.UnavailableFashionServiceIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 服务身份组合根；当前无 HTTP 端点，后续 adapter 必须显式调用该边界。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FashionServiceIdentityProperties.class)
public class FashionServiceIdentityConfiguration {

    @Bean(name = "fashionServiceReplayStore")
    @ConditionalOnMissingBean(FashionServiceReplayStore.class)
    public FashionServiceReplayStore fashionServiceReplayStore() {
        return new InMemoryFashionServiceReplayStore();
    }

    @Bean(name = "fashionServiceIdentity")
    @ConditionalOnMissingBean(FashionServiceIdentity.class)
    public FashionServiceIdentity fashionServiceIdentity(
            FashionServiceIdentityProperties properties,
            FashionServiceReplayStore replayStore) {
        boolean hasActiveKeyId = hasText(properties.getActiveKeyId());
        boolean hasActiveKey = hasText(properties.getActiveKeyBase64());
        boolean hasPreviousKeyId = hasText(properties.getPreviousKeyId());
        boolean hasPreviousKey = hasText(properties.getPreviousKeyBase64());
        if (!hasActiveKeyId && !hasActiveKey && !hasPreviousKeyId && !hasPreviousKey) {
            return new UnavailableFashionServiceIdentity();
        }
        if (hasActiveKeyId != hasActiveKey || hasPreviousKeyId != hasPreviousKey
                || (hasPreviousKeyId && !hasActiveKeyId)) {
            throw new IllegalStateException("Fashion 服务身份密钥配置不完整");
        }
        if (hasPreviousKeyId && properties.getActiveKeyId().equals(properties.getPreviousKeyId())) {
            throw new IllegalStateException("Fashion 当前密钥与上一代密钥必须使用不同 keyId");
        }

        FashionServiceIdentityPolicy policy = new FashionServiceIdentityPolicy(
                properties.getLocalServiceId(),
                properties.getPeerServiceId(),
                properties.getMaxClockSkew(),
                properties.getNonceTtl());
        Map<String, byte[]> keyMaterials = new LinkedHashMap<>();
        try {
            keyMaterials.put(properties.getActiveKeyId(), decodeKey(properties.getActiveKeyBase64()));
            if (hasPreviousKeyId) {
                keyMaterials.put(properties.getPreviousKeyId(), decodeKey(properties.getPreviousKeyBase64()));
            }
            FashionServiceIdentityKeyRing keyRing = FashionServiceIdentityKeyRing.of(
                    properties.getActiveKeyId(), keyMaterials);
            return new HmacSha256FashionServiceIdentity(
                    policy, keyRing, replayStore, Clock.systemUTC(), () -> UUID.randomUUID().toString());
        } finally {
            keyMaterials.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        }
    }

    private static byte[] decodeKey(String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException standardFailure) {
            try {
                return Base64.getUrlDecoder().decode(encoded);
            } catch (IllegalArgumentException urlFailure) {
                throw new IllegalStateException("Fashion 服务身份密钥必须是合法 Base64", urlFailure);
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
