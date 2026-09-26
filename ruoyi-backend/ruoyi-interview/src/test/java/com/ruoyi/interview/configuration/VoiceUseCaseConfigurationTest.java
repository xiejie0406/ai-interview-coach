package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ModelUsage;
import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.infrastructure.crypto.AesGcmSensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.provider.DisabledSpeechToTextAdapter;
import com.ruoyi.interview.infrastructure.provider.ProviderAdapter;
import com.ruoyi.interview.infrastructure.storage.LocalFileObjectStorageAdapter;
import com.ruoyi.interview.infrastructure.storage.UnavailableObjectStorageAdapter;
import com.ruoyi.interview.infrastructure.persistence.shared.UnavailableSensitiveEnvelopeCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceUseCaseConfigurationTest {
    @TempDir
    Path root;

    @Test
    void capabilityFailsClosedAtFirstUnavailableBoundary() {
        var configuration = new VoiceUseCaseConfiguration();
        var capability = configuration.voiceCapabilityPort(
                new UnavailableObjectStorageAdapter(), new DisabledSpeechToTextAdapter(),
                new UnavailableSensitiveEnvelopeCipher());

        var result = capability.current(TenantId.of("tenant-a"), UserId.of("42"));
        assertFalse(result.enabled());
        assertEquals(Optional.of("OBJECT_STORAGE_NOT_READY"), result.unavailableReasonCode());
    }

    @Test
    void testOnlyDeterministicAdapterCanEnableLocalCapability() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 3);
        var configuration = new VoiceUseCaseConfiguration();
        var capability = configuration.voiceCapabilityPort(
                new LocalFileObjectStorageAdapter(root), new TestOnlyDeterministicSpeechAdapter(),
                new AesGcmSensitiveEnvelopeCipher("test-key", key, new SecureRandom()));

        var result = capability.current(TenantId.of("tenant-a"), UserId.of("42"));
        assertTrue(result.enabled());
        assertTrue(result.unavailableReasonCode().isEmpty());
    }

    /** test-only fake；不会被 Spring 生产 profile 扫描或装配。 */
    private static final class TestOnlyDeterministicSpeechAdapter
            implements SpeechToTextPort, ProviderAdapter {
        @Override
        public Result transcribe(Request request, InvocationContext context) {
            return new Success("deterministic transcript", "zh-CN", "UTF16", List.of(),
                    new ModelUsage(List.of()), Optional.of("test-request-hash"));
        }

        @Override public String adapterId() { return "test-only-deterministic-asr"; }
        @Override public boolean available() { return true; }
        @Override public String reasonCode() { return "AVAILABLE"; }
    }
}
