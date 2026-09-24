package com.ruoyi.aden.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenWorkspaceMapperContractTest {
    @Test
    void mapperUsesMembershipJoinAndExplicitWorkspacePredicate() throws Exception {
        Path mapper = workspacePath("platform-backend", "ruoyi-aden", "src", "main", "resources",
                "mapper", "aden", "AdenWorkspaceMapper.xml");
        String xml = Files.readString(mapper, StandardCharsets.UTF_8);

        assertTrue(xml.contains("m.ruoyi_user_id = #{ruoYiUserId}"));
        assertTrue(xml.contains("m.workspace_id = #{workspaceId}"));
        assertTrue(xml.contains("m.member_status = 'ACTIVE'"));
        assertTrue(xml.contains("w.workspace_status = 'ACTIVE'"));
        assertTrue(xml.contains("insert into aden_audit_event"));
        assertFalse(xml.contains("${"));
    }

    @Test
    void securityChainAndControllerKeepApprovedBoundaries() throws Exception {
        String config = source("configuration", "AdenRuntimeConfiguration.java");
        String controller = source("api", "operator", "WorkspaceController.java");

        assertTrue(config.contains("@Order(2)"));
        assertTrue(config.contains("securityMatcher(\"/api/v1/aden/**\")"));
        assertTrue(config.contains("SessionCreationPolicy.STATELESS"));
        assertTrue(config.contains("csrf -> csrf.disable()"));
        assertTrue(controller.contains("@PreAuthorize(\"@ss.hasPermi('aden:workspace:list')\")"));
        assertTrue(controller.contains("@PreAuthorize(\"@ss.hasPermi('aden:workspace:create')\")"));
    }

    private static String source(String... parts) throws Exception {
        String[] prefix = {"platform-backend", "ruoyi-aden", "src", "main", "java", "com", "ruoyi", "aden"};
        String[] all = new String[prefix.length + parts.length];
        System.arraycopy(prefix, 0, all, 0, prefix.length);
        System.arraycopy(parts, 0, all, prefix.length, parts.length);
        return Files.readString(workspacePath(all), StandardCharsets.UTF_8);
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
