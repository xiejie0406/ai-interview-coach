package com.ruoyi.aden.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenTaskMapperContractTest {
    @Test
    void taskCasContainsEveryIsolationAndConcurrencyPredicate() throws Exception {
        Path mapper = workspacePath("ruoyi-backend", "ruoyi-aden", "src", "main", "resources",
                "mapper", "aden", "AdenTaskMapper.xml");
        String xml = Files.readString(mapper, StandardCharsets.UTF_8);

        assertTrue(xml.contains("workspace_id = #{workspaceId}"));
        assertTrue(xml.contains("task_id = #{taskId}"));
        assertTrue(xml.contains("version = #{expectedVersion}"));
        assertTrue(xml.contains("task_state = #{expectedState}"));
        assertTrue(xml.contains("version = #{nextVersion}"));
        assertFalse(xml.contains("${"));
    }

    private static Path workspacePath(String... parts) {
        Path current = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && current != null; up++, current = current.getParent()) {
            Path candidate = current;
            for (String part : parts) candidate = candidate.resolve(part);
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("无法定位 workspace 文件 " + String.join("/", parts));
    }
}
