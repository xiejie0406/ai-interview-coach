package com.ruoyi.fashion.infrastructure.airuntime.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 当前密钥与至多一个上一代密钥；轮换期间签名只用当前密钥，验签可接受两代。 */
public final class FashionServiceIdentityKeyRing {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final String activeKeyId;
    private final Map<String, SecretKey> verificationKeys;

    private FashionServiceIdentityKeyRing(String activeKeyId, Map<String, SecretKey> verificationKeys) {
        this.activeKeyId = activeKeyId;
        this.verificationKeys = Map.copyOf(verificationKeys);
    }

    public static FashionServiceIdentityKeyRing of(String activeKeyId, Map<String, byte[]> keyMaterials) {
        String validatedActiveKeyId = validateKeyId(activeKeyId);
        Objects.requireNonNull(keyMaterials, "keyMaterials");
        if (keyMaterials.isEmpty() || keyMaterials.size() > 2) {
            throw new IllegalArgumentException("key ring 必须包含当前密钥，且最多保留一代旧密钥");
        }
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        keyMaterials.forEach((keyId, material) -> {
            String validatedKeyId = validateKeyId(keyId);
            Objects.requireNonNull(material, "key material");
            if (material.length < MINIMUM_KEY_BYTES) {
                throw new IllegalArgumentException("HMAC 密钥至少需要 32 字节");
            }
            keys.put(validatedKeyId, new SecretKeySpec(material.clone(), "HmacSHA256"));
        });
        if (!keys.containsKey(validatedActiveKeyId)) {
            throw new IllegalArgumentException("activeKeyId 必须存在于 key ring");
        }
        return new FashionServiceIdentityKeyRing(validatedActiveKeyId, keys);
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    SecretKey activeKey() {
        return verificationKeys.get(activeKeyId);
    }

    Optional<SecretKey> verificationKey(String keyId) {
        return Optional.ofNullable(verificationKeys.get(keyId));
    }

    private static String validateKeyId(String keyId) {
        Objects.requireNonNull(keyId, "keyId");
        if (!keyId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw new IllegalArgumentException("keyId 格式非法");
        }
        return keyId;
    }
}
