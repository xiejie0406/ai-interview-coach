package com.ruoyi.aden.architecture;

import com.ruoyi.aden.application.workspace.AdenWorkspaceSecurityAuditService;
import com.ruoyi.aden.application.workspace.AdenWorkspaceService;
import com.ruoyi.aden.configuration.AdenSchedulingConfiguration;
import com.ruoyi.aden.infrastructure.id.UuidAdenIdGenerator;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Modifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenArchitectureTest {
    @Test
    void utcCodecRoundTripsMicrosecondPrecision() {
        Instant instant = Instant.parse("2026-09-13T12:34:56.123456Z");
        LocalDateTime databaseValue = AdenUtcDateTimeCodec.toDatabase(instant);
        assertEquals(instant, AdenUtcDateTimeCodec.fromDatabase(databaseValue));
    }

    @Test
    void generatedIdsAreCanonicalLowercaseUuidStrings() {
        String id = new UuidAdenIdGenerator().nextId();
        assertEquals(id, id.toLowerCase());
        assertEquals(id, UUID.fromString(id).toString());
        assertTrue(id.length() == 36);
    }

    @Test
    void domainLayerRemainsPureJava() throws IOException {
        Path domain = workspacePath("ruoyi-backend", "ruoyi-aden", "src", "main", "java",
                "com", "ruoyi", "aden", "domain");
        List<String> forbidden = List.of("org.springframework.", "org.mybatis.", "org.flywaydb.",
                "jakarta.", "com.ruoyi.aden.api.", "com.ruoyi.aden.infrastructure.");
        try (var sources = Files.walk(domain)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                for (String dependency : forbidden) {
                    assertTrue(!text.contains(dependency), source + " 不得依赖 " + dependency);
                }
            }
        }
    }

    @Test
    void transactionalApplicationServicesCanBeProxiedAndForbiddenAuditUsesANewTransaction()
            throws NoSuchMethodException, IOException {
        assertTrue(!Modifier.isFinal(AdenWorkspaceService.class.getModifiers()));
        Path application = workspacePath("ruoyi-backend", "ruoyi-aden", "src", "main", "java",
                "com", "ruoyi", "aden", "application");
        try (var sources = Files.walk(application)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                if (text.contains("@Transactional")) {
                    assertTrue(!text.matches("(?s).*public\\s+final\\s+class\\s+.*"),
                            source + " 使用 @Transactional 时不得声明为 final，否则 CGLIB 无法创建事务代理");
                }
            }
        }
        Transactional transactional = AdenWorkspaceSecurityAuditService.class
                .getMethod("recordForbidden",
                        com.ruoyi.aden.application.security.AdenOperatorPrincipal.class,
                        com.ruoyi.aden.domain.workspace.AdenWorkspaceId.class,
                        String.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void maintenanceCoordinatorIsEagerEvenWhenHostUsesLazyInitialization() {
        Lazy lazy = java.util.Arrays.stream(AdenSchedulingConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("adenMaintenanceCoordinator"))
                .findFirst().orElseThrow().getAnnotation(Lazy.class);
        assertTrue(lazy != null && !lazy.value(),
                "Aden Outbox/SSE 维护调度不得因宿主懒加载而静默停用");
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
