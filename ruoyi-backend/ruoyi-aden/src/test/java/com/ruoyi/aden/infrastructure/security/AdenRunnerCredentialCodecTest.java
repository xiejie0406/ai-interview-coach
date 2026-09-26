package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.domain.runner.AdenCredentialId;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenRunnerCredentialCodecTest {
    private static final AdenCredentialId ID = new AdenCredentialId(
            "11111111-1111-4111-8111-111111111111");

    @Test
    void issuesPublicIdDotRandomSecretAndVerifiesOnlyWithCorrectPepper() {
        AdenRunnerCredentialCodec codec = new AdenRunnerCredentialCodec(new SecureRandom());
        byte[] pepper = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        var first = codec.issue(ID, "pepper-v1", pepper);
        var second = codec.issue(ID, "pepper-v1", pepper);

        assertTrue(first.bearer().startsWith(ID.value() + "."));
        assertTrue(first.keyedDigest().matches("[a-f0-9]{64}"));
        assertNotEquals(first.bearer(), second.bearer());
        assertTrue(codec.verify(first.bearer(), ID, first.keyedDigest(), pepper));
        assertFalse(codec.verify(first.bearer(), ID, first.keyedDigest(), "x".repeat(32).getBytes()));
        assertFalse(codec.verify(first.bearer() + "x", ID, first.keyedDigest(), pepper));
        assertFalse(codec.verify("malformed", ID, first.keyedDigest(), pepper));
    }
}
