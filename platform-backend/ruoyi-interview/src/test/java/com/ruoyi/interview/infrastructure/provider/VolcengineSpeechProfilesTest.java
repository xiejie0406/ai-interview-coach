package com.ruoyi.interview.infrastructure.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VolcengineSpeechProfilesTest {
    @Test
    void blankResourceIdUsesModelMapping() {
        String mapped = VolcengineSpeechProfiles.asrResourceId("doubao-streaming-asr-2.0");

        assertEquals("volc.seedasr.sauc.duration",
                VolcengineSpeechProfiles.configuredResourceId(null, mapped));
    }

    @Test
    void configuredResourceIdOverridesModelMapping() {
        assertEquals("volc.account.enabled.resource",
                VolcengineSpeechProfiles.configuredResourceId(
                        "volc.account.enabled.resource", "volc.default"));
    }

    @Test
    void malformedConfiguredResourceIdIsRejectedWithoutSilentFallback() {
        assertNull(VolcengineSpeechProfiles.configuredResourceId(
                "invalid resource id", "volc.default"));
    }
}
