package com.ruoyi.fashion.infrastructure.airuntime.security;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** FASHION-HMAC-SHA256 v1 的跨语言规范化与签名实现。 */
public final class FashionServiceSignatureV1 {

    public static final String ALGORITHM = "FASHION-HMAC-SHA256";
    public static final String SIGNATURE_PREFIX = "v1=";

    private FashionServiceSignatureV1() {
    }

    public static String bodyDigest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    public static String canonical(
            String method,
            String rawPath,
            String serviceId,
            String audience,
            String timestamp,
            String nonce,
            String contentSha256) {
        return ALGORITHM + '\n'
                + method + '\n'
                + rawPath + '\n'
                + serviceId + '\n'
                + audience + '\n'
                + timestamp + '\n'
                + nonce + '\n'
                + contentSha256;
    }

    static String sign(SecretKey key, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return SIGNATURE_PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("当前 JRE 不支持 HmacSHA256", exception);
        }
    }

    static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }
}
