package com.ruoyi.interview.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMigrationContractTest {
    @Test
    void migrationsUseRuoyiBusinessReferencesAndDurableConstraints() throws IOException {
        String foundation = migration("V3__voice_interview_governance_foundation.sql");
        String voice = migration("V4__voice_artifacts_transcripts_and_execution.sql");
        String stream = migration("V5__durable_stream_events.sql");

        String combined = foundation + voice + stream;
        assertFalse(combined.contains("identity."));
        assertTrue(foundation.contains("ruoyi_user_id bigint"));
        assertTrue(voice.contains("transcript_version_immutable"));
        assertTrue(voice.contains("object_ciphertext bytea"));
        assertTrue(stream.contains("stream_head_monotonic"));
        assertTrue(stream.contains("unique (tenant_id, event_id)"));
    }

    private static String migration(String name) throws IOException {
        try (var input = VoiceMigrationContractTest.class.getResourceAsStream("/db/migration/" + name)) {
            if (input == null) throw new IOException("missing migration " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
