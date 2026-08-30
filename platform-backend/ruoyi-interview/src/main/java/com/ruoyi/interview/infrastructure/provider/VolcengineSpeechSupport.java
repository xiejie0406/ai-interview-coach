package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.application.agent.port.ProviderFailure;
import com.ruoyi.interview.domain.platform.RetryDisposition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

final class VolcengineSpeechSupport {
    private VolcengineSpeechSupport() {
    }

    static ProviderFailure failure(String errorClass, RetryDisposition disposition,
                                   Optional<Duration> retryAfter, Optional<String> requestIdHash) {
        return new ProviderFailure(errorClass, disposition, retryAfter, requestIdHash);
    }

    static Optional<String> requestIdHash(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestId.getBytes(StandardCharsets.UTF_8));
            return Optional.of(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("required SHA-256 digest is unavailable", exception);
        }
    }

    static RetryDisposition dispositionForProviderCode(int code) {
        if (code == 55000031 || code >= 55000000) {
            return RetryDisposition.SAFE_BACKOFF;
        }
        if (code == 45000081) {
            return RetryDisposition.SAFE_BACKOFF;
        }
        return RetryDisposition.NOT_RETRYABLE;
    }
}

