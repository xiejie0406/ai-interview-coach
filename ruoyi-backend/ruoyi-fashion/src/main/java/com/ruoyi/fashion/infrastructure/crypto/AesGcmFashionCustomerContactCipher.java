package com.ruoyi.fashion.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmFashionCustomerContactCipher implements FashionCustomerContactCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;

    public AesGcmFashionCustomerContactCipher(String activeKeyId, Map<String, byte[]> keyMaterials) {
        if (activeKeyId == null || !activeKeyId.matches("[A-Za-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("客户联系电话 activeKeyId 格式无效");
        }
        LinkedHashMap<String, SecretKey> values = new LinkedHashMap<>();
        keyMaterials.forEach((keyId, material) -> {
            if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,48}")) {
                throw new IllegalArgumentException("客户联系电话 keyId 格式无效");
            }
            if (material == null || !(material.length == 16 || material.length == 24 || material.length == 32)) {
                throw new IllegalArgumentException("客户联系电话 AES 密钥必须为 16、24 或 32 字节");
            }
            values.put(keyId, new SecretKeySpec(material.clone(), "AES"));
        });
        if (!values.containsKey(activeKeyId)) throw new IllegalArgumentException("activeKeyId 没有对应密钥");
        this.activeKeyId = activeKeyId;
        this.keys = Map.copyOf(values);
    }

    @Override
    public String encrypt(long customerId, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(binding(customerId));
            byte[] body = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1." + activeKeyId + "." + ENCODER.encodeToString(nonce) + "." + ENCODER.encodeToString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("客户联系电话加密失败", exception);
        }
    }

    @Override
    public String decrypt(long customerId, String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        String[] parts = ciphertext.split("\\.", -1);
        if (parts.length != 4 || !"v1".equals(parts[0]) || !keys.containsKey(parts[1])) {
            throw new IllegalStateException("客户联系电话密文格式或 keyId 无效");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.get(parts[1]), new GCMParameterSpec(128, DECODER.decode(parts[2])));
            cipher.updateAAD(binding(customerId));
            return new String(cipher.doFinal(DECODER.decode(parts[3])), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("客户联系电话解密失败", exception);
        }
    }

    private static byte[] binding(long customerId) {
        return ("fq_customer:" + customerId + ":contact_phone").getBytes(StandardCharsets.UTF_8);
    }
}
