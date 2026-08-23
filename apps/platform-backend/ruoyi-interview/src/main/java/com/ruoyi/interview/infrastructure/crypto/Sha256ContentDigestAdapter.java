package com.ruoyi.interview.infrastructure.crypto;

import com.ruoyi.interview.application.voice.port.ContentDigestPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 仅生成不可逆内容摘要；不得记录传入正文。 */
public final class Sha256ContentDigestAdapter implements ContentDigestPort {

    @Override
    public String digest(String confidentialText) {
        if (confidentialText == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(confidentialText.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("required SHA-256 digest is unavailable", exception);
        }
    }
}

