package com.ruoyi.system.secret;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** MySQL 密文的封装；根密钥是无法由同库自举的唯一解密材料。 */
@Component
public final class ManagedSecretCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_ENV = "RUOYI_MANAGED_SECRET_MASTER_KEY_BASE64";
    private static final String KEY_ID_ENV = "RUOYI_MANAGED_SECRET_MASTER_KEY_ID";
    private static final String PREVIOUS_KEY_ENV = "RUOYI_MANAGED_SECRET_PREVIOUS_MASTER_KEY_BASE64";
    private static final String PREVIOUS_KEY_ID_ENV = "RUOYI_MANAGED_SECRET_PREVIOUS_MASTER_KEY_ID";

    public record Envelope(String keyId, byte[] nonce, byte[] ciphertext) { }

    public Envelope encrypt(String kind, String alias, int version, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("密钥值不能为空");
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(keyId()), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(kind, alias, version));
            return new Envelope(keyId(), nonce, cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("密钥加密失败", exception);
        }
    }

    public String decrypt(String kind, String alias, int version, Envelope envelope) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey(envelope.keyId()), new GCMParameterSpec(128, envelope.nonce()));
            cipher.updateAAD(aad(kind, alias, version));
            return new String(cipher.doFinal(envelope.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("密钥解密失败或密文被篡改", exception);
        }
    }

    private static byte[] aad(String kind, String alias, int version) {
        return (kind + "\n" + alias + "\n" + version).getBytes(StandardCharsets.UTF_8);
    }

    private static String keyId() {
        String value = System.getenv(KEY_ID_ENV);
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalStateException("密钥根版本未配置");
        }
        return value;
    }

    private static SecretKeySpec masterKey(String requestedId) {
        String encoded;
        if (keyId().equals(requestedId)) {
            encoded = System.getenv(KEY_ENV);
        } else if (requestedId != null && requestedId.equals(System.getenv(PREVIOUS_KEY_ID_ENV))) {
            encoded = System.getenv(PREVIOUS_KEY_ENV);
        } else {
            throw new IllegalStateException("未知密钥根版本");
        }
        if (encoded == null || encoded.isBlank()) throw new IllegalStateException("密钥根未配置");
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            if (key.length != 32) throw new IllegalStateException("密钥根必须为 32 字节");
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("密钥根格式无效", exception);
        }
    }
}
