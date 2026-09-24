package com.ruoyi.interview.infrastructure.provider;

/** 火山控制台展示名/项目 profile 到官方 Resource ID 的最小映射。 */
final class VolcengineSpeechProfiles {
    private static final int MAX_RESOURCE_ID_LENGTH = 128;

    private VolcengineSpeechProfiles() {
    }

    static String asrResourceId(String profile) {
        String value = normalized(profile);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "doubao-streaming-asr-2.0" -> "volc.seedasr.sauc.duration";
            case "doubao-streaming-asr-2.0-concurrent" -> "volc.seedasr.sauc.concurrent";
            default -> value.startsWith("volc.") ? value : null;
        };
    }

    static String ttsResourceId(String profile) {
        String value = normalized(profile);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "doubao-tts-2.0" -> "seed-tts-2.0";
            default -> value.startsWith("seed-") ? value : null;
        };
    }

    static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String configuredResourceId(String configured, String fallback) {
        String value = normalized(configured);
        if (value == null) {
            return fallback;
        }
        if (value.length() > MAX_RESOURCE_ID_LENGTH
                || !value.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return value;
    }
}

