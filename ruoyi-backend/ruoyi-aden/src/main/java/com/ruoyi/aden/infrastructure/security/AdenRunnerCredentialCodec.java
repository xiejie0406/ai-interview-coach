package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.domain.runner.AdenCredentialId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** 生成 credentialId.secret，仅返回一次明文；持久化层只接收 keyed digest。 */
public final class AdenRunnerCredentialCodec {
    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom;

    public AdenRunnerCredentialCodec(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public IssuedCredential issue(AdenCredentialId credentialId, String pepperKeyId, byte[] pepper) {
        Objects.requireNonNull(credentialId, "credentialId");
        requirePepperKeyId(pepperKeyId);
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String bearer = credentialId.value() + "." + encodedSecret;
        return new IssuedCredential(bearer, digest(bearer, pepper), pepperKeyId);
    }

    public boolean verify(String bearer, AdenCredentialId expectedId,
                          String expectedDigest, byte[] pepper) {
        Objects.requireNonNull(expectedId, "expectedId");
        if (bearer == null || expectedDigest == null || !expectedDigest.matches("[a-f0-9]{64}")) return false;
        int separator = bearer.indexOf('.');
        if (separator <= 0 || separator != bearer.lastIndexOf('.')) return false;
        try {
            AdenCredentialId actualId = new AdenCredentialId(bearer.substring(0, separator));
            byte[] decoded = Base64.getUrlDecoder().decode(bearer.substring(separator + 1));
            if (!actualId.equals(expectedId) || decoded.length != SECRET_BYTES) return false;
            byte[] actual = HexFormat.of().parseHex(digest(bearer, pepper));
            byte[] expected = HexFormat.of().parseHex(expectedDigest);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static String digest(String bearer, byte[] pepper) {
        Objects.requireNonNull(pepper, "pepper");
        if (pepper.length < 32) throw new IllegalArgumentException("Runner pepper 至少 32 字节");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.clone(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    ("aden-runner-credential-v1\0" + bearer).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("JVM 不支持 HmacSHA256", impossible);
        }
    }

    private static void requirePepperKeyId(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("pepperKeyId 不合法");
        }
    }

    public record IssuedCredential(String bearer, String keyedDigest, String pepperKeyId) {
        public IssuedCredential {
            Objects.requireNonNull(bearer, "bearer");
            Objects.requireNonNull(keyedDigest, "keyedDigest");
            Objects.requireNonNull(pepperKeyId, "pepperKeyId");
        }
    }
}
