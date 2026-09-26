package com.ruoyi.interview.infrastructure.provider;

import java.util.Locale;
import java.util.function.BiConsumer;

/** 火山语音鉴权配置；新旧控制台凭据必须显式选择且禁止混用。 */
final class VolcengineSpeechCredentials {
    static final String MODE_REQUIRED = "SPEECH_AUTH_MODE_REQUIRED";
    static final String CONFIGURATION_INVALID = "SPEECH_AUTH_CONFIGURATION_INVALID";

    private final String apiKey;
    private final String appId;
    private final String accessToken;
    private final String reasonCode;

    private VolcengineSpeechCredentials(String apiKey, String appId, String accessToken,
                                        String reasonCode) {
        this.apiKey = apiKey;
        this.appId = appId;
        this.accessToken = accessToken;
        this.reasonCode = reasonCode;
    }

    static VolcengineSpeechCredentials from(String mode, String apiKey,
                                             String appId, String accessToken) {
        String normalizedMode = normalized(mode);
        String normalizedApiKey = normalized(apiKey);
        String normalizedAppId = normalized(appId);
        String normalizedAccessToken = normalized(accessToken);
        if (normalizedMode == null) {
            return unavailable(MODE_REQUIRED);
        }
        return switch (normalizedMode.toLowerCase(Locale.ROOT)) {
            case "api-key" -> normalizedApiKey != null
                    && normalizedAppId == null && normalizedAccessToken == null
                    ? new VolcengineSpeechCredentials(normalizedApiKey, null, null, null)
                    : unavailable(CONFIGURATION_INVALID);
            case "access-token" -> normalizedApiKey == null
                    && normalizedAppId != null && normalizedAccessToken != null
                    ? new VolcengineSpeechCredentials(null, normalizedAppId,
                            normalizedAccessToken, null)
                    : unavailable(CONFIGURATION_INVALID);
            default -> unavailable(CONFIGURATION_INVALID);
        };
    }

    boolean available() {
        return reasonCode == null;
    }

    String reasonCode() {
        return reasonCode;
    }

    void forEachHeader(BiConsumer<String, String> consumer) {
        if (!available()) {
            throw new IllegalStateException(reasonCode);
        }
        if (apiKey != null) {
            consumer.accept("X-Api-Key", apiKey);
            return;
        }
        consumer.accept("X-Api-App-Key", appId);
        consumer.accept("X-Api-Access-Key", accessToken);
    }

    private static VolcengineSpeechCredentials unavailable(String reasonCode) {
        return new VolcengineSpeechCredentials(null, null, null, reasonCode);
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
