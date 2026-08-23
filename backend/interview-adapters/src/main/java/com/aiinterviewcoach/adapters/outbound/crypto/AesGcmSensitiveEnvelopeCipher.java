package com.aiinterviewcoach.adapters.outbound.crypto;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.EncryptedEnvelope;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.SensitiveEnvelopeCipher;
import com.aiinterviewcoach.domain.platform.TenantId;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AES-256-GCM 敏感字段信封实现。密钥只由 Boot 的外部 secret source 注入；信封仅保存 key id、
 * nonce、密文和 AAD hash。tenant 与字段绑定同时进入 AAD，防止跨 tenant/列复制密文。
 */
public final class AesGcmSensitiveEnvelopeCipher implements SensitiveEnvelopeCipher {

    public static final String ALGORITHM = "AES-256-GCM";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String currentKeyId;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom random;

    /** 单当前密钥兼容入口；后续轮换可改用 keyring 构造器并保留旧 key id 仅供解密。 */
    public AesGcmSensitiveEnvelopeCipher(
            String currentKeyId,
            byte[] currentKey,
            SecureRandom random
    ) {
        this(currentKeyId, Map.of(requireText(currentKeyId, "currentKeyId"),
                java.util.Objects.requireNonNull(currentKey, "currentKey")), random);
    }

    public AesGcmSensitiveEnvelopeCipher(
            String currentKeyId,
            Map<String, byte[]> keyring,
            SecureRandom random
    ) {
        this.currentKeyId = requireText(currentKeyId, "currentKeyId");
        this.random = java.util.Objects.requireNonNull(random, "secureRandom");
        if (keyring == null || keyring.isEmpty()) {
            throw new IllegalArgumentException("sensitive data keyring must not be empty");
        }
        LinkedHashMap<String, SecretKeySpec> checked = new LinkedHashMap<>();
        keyring.forEach((keyId, material) -> {
            String checkedId = requireText(keyId, "keyId");
            byte[] key = java.util.Objects.requireNonNull(material, "key material").clone();
            if (key.length != AES_256_KEY_BYTES) {
                throw new IllegalArgumentException("sensitive data keys must be exactly 256 bits");
            }
            if (checked.put(checkedId, new SecretKeySpec(key, "AES")) != null) {
                throw new IllegalArgumentException("duplicate sensitive data key id");
            }
            java.util.Arrays.fill(key, (byte) 0);
        });
        if (!checked.containsKey(this.currentKeyId)) {
            throw new IllegalArgumentException("current sensitive data key id is not in keyring");
        }
        this.keys = Map.copyOf(checked);
    }

    /** Boot 唯一 Base64 解码入口；拒绝 MIME/URL 变体、空值和非 256-bit key。 */
    public static byte[] decodeBase64Key(String encoded, String propertyName) {
        String checked = requireText(encoded, requireText(propertyName, "propertyName"));
        try {
            byte[] decoded = Base64.getDecoder().decode(checked);
            if (decoded.length != AES_256_KEY_BYTES) {
                java.util.Arrays.fill(decoded, (byte) 0);
                throw new IllegalArgumentException(propertyName + " must decode to exactly 256 bits");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("exactly 256 bits")) {
                throw exception;
            }
            throw new IllegalArgumentException(propertyName + " must be strict Base64 key material", exception);
        }
    }

    @Override
    public EncryptedEnvelope encrypt(TenantId tenantId, String aadBinding, String plaintext) {
        byte[] aad = aad(tenantId, aadBinding);
        byte[] cleartext = requireText(plaintext, "plaintext").getBytes(StandardCharsets.UTF_8);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(cleartext);
            return new EncryptedEnvelope(currentKeyId, ALGORITHM, nonce, ciphertext, sha256(aad));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("required sensitive data cipher is unavailable", exception);
        } finally {
            java.util.Arrays.fill(cleartext, (byte) 0);
            java.util.Arrays.fill(aad, (byte) 0);
        }
    }

    @Override
    public String decrypt(TenantId tenantId, String aadBinding, EncryptedEnvelope envelope) {
        java.util.Objects.requireNonNull(envelope, "encryptedEnvelope");
        if (!ALGORITHM.equals(envelope.algorithm())) {
            throw new IllegalArgumentException("unsupported sensitive data envelope algorithm");
        }
        SecretKeySpec key = keys.get(envelope.keyId());
        if (key == null) {
            throw new IllegalStateException("sensitive data decryption key is unavailable");
        }
        byte[] aad = aad(tenantId, aadBinding);
        byte[] expectedHash = sha256(aad).getBytes(StandardCharsets.US_ASCII);
        byte[] actualHash = envelope.aadHash().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            java.util.Arrays.fill(aad, (byte) 0);
            throw new IllegalArgumentException("sensitive data envelope AAD does not match its owner");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, envelope.nonce()));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(envelope.ciphertext()), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw new IllegalArgumentException("sensitive data envelope authentication failed", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("required sensitive data cipher is unavailable", exception);
        } finally {
            java.util.Arrays.fill(aad, (byte) 0);
            java.util.Arrays.fill(expectedHash, (byte) 0);
            java.util.Arrays.fill(actualHash, (byte) 0);
        }
    }

    private static byte[] aad(TenantId tenantId, String binding) {
        byte[] tenant = java.util.Objects.requireNonNull(tenantId, "tenantId")
                .value().getBytes(StandardCharsets.UTF_8);
        byte[] field = requireText(binding, "aadBinding").getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(Integer.BYTES + tenant.length + Integer.BYTES + field.length)
                .putInt(tenant.length).put(tenant).putInt(field.length).put(field).array();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("required content digest is unavailable", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "AesGcmSensitiveEnvelopeCipher[currentKeyId=" + currentKeyId + ", keys=<redacted>]";
    }
}
