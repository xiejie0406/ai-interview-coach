package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.domain.runner.AdenSessionId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** 生成 sessionId.secret；Session Secret 与设备凭据使用不同 HMAC domain。 */
public final class AdenRunnerSessionCodec {
    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom;

    public AdenRunnerSessionCodec(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public IssuedSession issue(AdenSessionId sessionId, String pepperKeyId, byte[] pepper) {
        Objects.requireNonNull(sessionId, "sessionId");
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String bearer = sessionId.value() + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedSession(bearer, digest(bearer, pepper), pepperKeyId);
    }

    public boolean verify(String bearer, AdenSessionId expectedId, String expectedDigest, byte[] pepper) {
        if (bearer == null || expectedDigest == null || !expectedDigest.matches("[a-f0-9]{64}")) return false;
        int separator = bearer.indexOf('.');
        if (separator <= 0 || separator != bearer.lastIndexOf('.')) return false;
        try {
            if (!new AdenSessionId(bearer.substring(0, separator)).equals(expectedId)
                    || Base64.getUrlDecoder().decode(bearer.substring(separator + 1)).length != SECRET_BYTES) return false;
            return MessageDigest.isEqual(HexFormat.of().parseHex(digest(bearer, pepper)),
                    HexFormat.of().parseHex(expectedDigest));
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
                    ("aden-runner-session-v1\0" + bearer).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("JVM 不支持 HmacSHA256", impossible);
        }
    }

    public record IssuedSession(String bearer, String keyedDigest, String pepperKeyId) { }
}
