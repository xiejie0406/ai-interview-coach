package com.ruoyi.aden.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenOutboxMapperContractTest {
    @Test
    void claimUsesSkipLockedAndEverySettlementUsesWorkspaceAndClaimToken() throws Exception {
        String sql = Files.readString(workspacePath(
                "platform-backend", "ruoyi-aden", "src", "main", "resources",
                "mapper", "aden", "AdenOutboxMapper.xml")).toLowerCase();

        assertTrue(sql.contains("for update skip locked"));
        assertTrue(sql.contains("order by o.available_at, o.workspace_id, o.outbox_id"));
        assertTrue(occurrences(sql, "and claim_token = #{claimtoken}") >= 3);
        assertTrue(occurrences(sql, "where workspace_id = #{workspaceid}") >= 4);
        assertFalse(sql.contains("${"));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }

    private static Path workspacePath(String... parts) {
        Path current = Path.of("").toAbsolutePath();
        for (int up = 0; up < 5 && current != null; up++, current = current.getParent()) {
            Path candidate = current;
            for (String part : parts) candidate = candidate.resolve(part);
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("无法定位 AdenOutboxMapper.xml");
    }
}
