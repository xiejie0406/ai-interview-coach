package com.ruoyi.interview.infrastructure.provider;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcengineSpeechCredentialsTest {
    @Test
    void apiKeyModeOnlyEmitsNewConsoleHeader() {
        var credentials = VolcengineSpeechCredentials.from(
                "api-key", "new-key", null, null);

        assertTrue(credentials.available());
        assertEquals(Map.of("X-Api-Key", "new-key"), headers(credentials));
    }

    @Test
    void accessTokenModeOnlyEmitsLegacyConsoleHeaders() {
        var credentials = VolcengineSpeechCredentials.from(
                "access-token", null, "app-id", "access-token");

        assertTrue(credentials.available());
        assertEquals(Map.of(
                "X-Api-App-Key", "app-id",
                "X-Api-Access-Key", "access-token"), headers(credentials));
    }

    @Test
    void modeIsRequiredEvenWhenAKeyExists() {
        var credentials = VolcengineSpeechCredentials.from(
                null, "ambiguous-key", null, null);

        assertFalse(credentials.available());
        assertEquals(VolcengineSpeechCredentials.MODE_REQUIRED, credentials.reasonCode());
        assertThrows(IllegalStateException.class, () -> headers(credentials));
    }

    @Test
    void mixedOrIncompleteCredentialsAreRejected() {
        var mixed = VolcengineSpeechCredentials.from(
                "api-key", "new-key", "app-id", "access-token");
        var incomplete = VolcengineSpeechCredentials.from(
                "access-token", null, "app-id", null);

        assertFalse(mixed.available());
        assertFalse(incomplete.available());
        assertEquals(VolcengineSpeechCredentials.CONFIGURATION_INVALID, mixed.reasonCode());
        assertEquals(VolcengineSpeechCredentials.CONFIGURATION_INVALID, incomplete.reasonCode());
    }

    private static Map<String, String> headers(VolcengineSpeechCredentials credentials) {
        Map<String, String> headers = new LinkedHashMap<>();
        credentials.forEachHeader(headers::put);
        return headers;
    }
}
