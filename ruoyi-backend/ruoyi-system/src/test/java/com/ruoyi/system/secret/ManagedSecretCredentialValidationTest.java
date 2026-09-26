package com.ruoyi.system.secret;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManagedSecretCredentialValidationTest {
    @Test
    void fixedProviderAliasesRejectWrongOwnerAndMixedSpeechModes() {
        assertThrows(IllegalArgumentException.class, () -> ManagedSecretService.validateKnownCredential(
                "AI", "ai.interview.deepseek", "fashion", "deepseek", "chat", "api-key", "synthetic"));
        assertThrows(IllegalArgumentException.class, () -> ManagedSecretService.validateKnownCredential(
                "AI", "ai.interview.volcengine.speech", "interview", "volcengine", "speech",
                "access-token", "{\"appId\":\"synthetic-app\"}"));
        assertThrows(IllegalArgumentException.class, () -> ManagedSecretService.validateKnownCredential(
                "AI", "ai.interview.volcengine.speech", "interview", "volcengine", "speech",
                "api-key", "{\"appId\":\"synthetic-app\",\"accessToken\":\"synthetic-token\"}"));
        assertDoesNotThrow(() -> ManagedSecretService.validateKnownCredential(
                "AI", "ai.interview.volcengine.speech", "interview", "volcengine", "speech",
                "access-token", "{\"appId\":\"synthetic-app\",\"accessToken\":\"synthetic-token\"}"));
    }
}
