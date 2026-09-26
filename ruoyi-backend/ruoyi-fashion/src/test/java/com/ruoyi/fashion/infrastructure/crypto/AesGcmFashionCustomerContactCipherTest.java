package com.ruoyi.fashion.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AesGcmFashionCustomerContactCipherTest {
    @Test
    void encryptsWithCustomerBindingAndSupportsKeyRotationReads() {
        byte[] oldKey = new byte[32];
        byte[] newKey = new byte[32];
        java.util.Arrays.fill(oldKey, (byte) 7);
        java.util.Arrays.fill(newKey, (byte) 9);
        AesGcmFashionCustomerContactCipher oldCipher =
                new AesGcmFashionCustomerContactCipher("old", Map.of("old", oldKey));
        String oldEnvelope = oldCipher.encrypt(1001L, "13800138000");
        AesGcmFashionCustomerContactCipher rotated =
                new AesGcmFashionCustomerContactCipher("new", Map.of("old", oldKey, "new", newKey));

        assertNotEquals("13800138000", oldEnvelope);
        assertEquals("13800138000", rotated.decrypt(1001L, oldEnvelope));
        assertEquals("13800138000", rotated.decrypt(1001L, rotated.encrypt(1001L, "13800138000")));
        assertThrows(IllegalStateException.class, () -> rotated.decrypt(1002L, oldEnvelope));
    }
}
