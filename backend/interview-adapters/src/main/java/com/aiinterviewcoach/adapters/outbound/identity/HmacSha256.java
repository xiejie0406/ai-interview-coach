package com.aiinterviewcoach.adapters.outbound.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** 进程内 HMAC helper；key 只由 Boot secret source 注入，永不输出。 */
final class HmacSha256 {

    private final byte[] key;

    HmacSha256(byte[] key) {
        this.key = java.util.Objects.requireNonNull(key, "HMAC key").clone();
        if (this.key.length < 32) {
            throw new IllegalArgumentException("HMAC key must contain at least 256 bits");
        }
    }

    String hash(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("required HMAC algorithm is unavailable", exception);
        }
    }

    @Override
    public String toString() {
        return "HmacSha256[key=<redacted>]";
    }
}
