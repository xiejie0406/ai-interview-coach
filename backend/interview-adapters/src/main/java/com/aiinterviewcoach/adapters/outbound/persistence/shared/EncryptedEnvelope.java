package com.aiinterviewcoach.adapters.outbound.persistence.shared;

import java.util.Objects;

/**
 * 持久化层的加密信封；只保存密钥引用和密文，不保存明文或可逆的“临时编码”。
 */
public record EncryptedEnvelope(
        String keyId,
        String algorithm,
        byte[] nonce,
        byte[] ciphertext,
        String aadHash
) {
    public EncryptedEnvelope {
        keyId = requireText(keyId, "keyId");
        algorithm = requireText(algorithm, "algorithm");
        nonce = Objects.requireNonNull(nonce, "nonce").clone();
        ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
        aadHash = requireText(aadHash, "aadHash");
        if (nonce.length == 0 || ciphertext.length == 0) {
            throw new IllegalArgumentException("encrypted envelope bytes must not be empty");
        }
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public String toString() {
        return "EncryptedEnvelope[redacted]";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
