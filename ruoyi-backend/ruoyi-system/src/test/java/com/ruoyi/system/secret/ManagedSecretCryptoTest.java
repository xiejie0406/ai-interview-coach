package com.ruoyi.system.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ManagedSecretCryptoTest {
    @Test
    void bindsCiphertextToKindAliasAndVersionAndRejectsTampering() {
        assumeTrue(System.getenv("RUOYI_MANAGED_SECRET_MASTER_KEY_ID") != null
                && System.getenv("RUOYI_MANAGED_SECRET_MASTER_KEY_BASE64") != null,
                "此测试需一次性合成根密钥环境");
        ManagedSecretCrypto crypto = new ManagedSecretCrypto();
        ManagedSecretCrypto.Envelope envelope = crypto.encrypt("AI", "ai.test.synthetic", 7, "synthetic-secret-value");
        assertEquals("synthetic-secret-value", crypto.decrypt("AI", "ai.test.synthetic", 7, envelope));
        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt("PLATFORM", "ai.test.synthetic", 7, envelope));
        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt("AI", "ai.test.synthetic", 8, envelope));
        byte[] tampered = Arrays.copyOf(envelope.ciphertext(), envelope.ciphertext().length);
        tampered[0] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt("AI", "ai.test.synthetic", 7,
                        new ManagedSecretCrypto.Envelope(envelope.keyId(), envelope.nonce(), tampered)));
    }
}
