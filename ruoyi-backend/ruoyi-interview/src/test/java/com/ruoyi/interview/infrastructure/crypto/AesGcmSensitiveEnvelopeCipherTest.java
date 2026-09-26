package com.ruoyi.interview.infrastructure.crypto;

import com.ruoyi.interview.domain.platform.TenantId;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmSensitiveEnvelopeCipherTest {
    @Test
    void bindsCiphertextToTenantAndFieldWithoutLeakingKeyMaterial() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        var cipher = new AesGcmSensitiveEnvelopeCipher("voice-key-v1", key, new SecureRandom());
        var envelope = cipher.encrypt(TenantId.of("tenant-a"), "voice:artifact-a", "opaque-reference");

        assertEquals("opaque-reference", cipher.decrypt(
                TenantId.of("tenant-a"), "voice:artifact-a", envelope));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(
                TenantId.of("tenant-b"), "voice:artifact-a", envelope));
        assertFalse(cipher.toString().contains("BwcH"));
    }
}
