package com.ruoyi.aden.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenMigrationStaticContractTest {
    private static final Pattern CREATE_TABLE = Pattern.compile("create\\s+table\\s+(aden_[a-z_]+)",
            Pattern.CASE_INSENSITIVE);

    @Test
    void migrationsCreateCoreAndApprovedCollectionWorkspaceScopedTables() throws IOException {
        List<Path> migrations = Files.list(migrationDirectory()).sorted().toList();
        assertEquals(List.of(
                "V1__aden_workspace.sql", "V2__aden_task.sql",
                "V3__aden_runner.sql", "V4__aden_reliability.sql", "V5__aden_collection.sql"),
                migrations.stream().map(path -> path.getFileName().toString()).toList());

        List<String> tables = new ArrayList<>();
        long workspaceColumns = 0;
        long utcDateTimeColumns = 0;
        for (Path migration : migrations) {
            String sql = Files.readString(migration, StandardCharsets.UTF_8);
            Matcher matcher = CREATE_TABLE.matcher(sql);
            while (matcher.find()) tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            assertFalse(sql.toLowerCase(Locale.ROOT).contains("drop table"));
            assertFalse(sql.toLowerCase(Locale.ROOT).contains("sys_user"));
            workspaceColumns += Pattern.compile("workspace_id\\s+char\\(36\\)\\s+character set ascii collate ascii_bin",
                    Pattern.CASE_INSENSITIVE).matcher(sql).results().count();
            utcDateTimeColumns += Pattern.compile("(?im)^\\s*[a-z_]+\\s+datetime\\(6\\)")
                    .matcher(sql).results().count();
            assertFalse(sql.toLowerCase(Locale.ROOT).contains(" timestamp"));
        }
        assertEquals(List.of(
                "aden_workspace", "aden_workspace_member", "aden_task", "aden_task_step",
                "aden_runner", "aden_runner_credential", "aden_runner_session", "aden_runner_delivery",
                "aden_event", "aden_outbox", "aden_inbox", "aden_audit_event",
                "aden_collection_item", "aden_collection_snapshot", "aden_collection_manifest",
                "aden_collection_curation", "aden_collection_upload", "aden_collection_export"), tables);
        assertEquals(18L, workspaceColumns);
        assertEquals(new java.util.HashSet<>(tables), AdenDatabasePreconditions.EXPECTED_BUSINESS_TABLES,
                "DDL 表集合必须同步到启动 guard、CLI migrate/validate 共用的严格白名单");
        assertEquals(50L, utcDateTimeColumns);
        assertTrue(Files.readString(migrations.get(0)).contains("last_event_seq bigint"));

        String runnerSql = Files.readString(migrations.get(2), StandardCharsets.UTF_8);
        assertTrue(runnerSql.contains("current_session_epoch bigint"));
        assertTrue(runnerSql.contains("credential_keyed_digest"));
        assertTrue(runnerSql.contains("session_keyed_digest"));
        assertTrue(runnerSql.contains("latest_receipt_sequence bigint"));
        assertTrue(runnerSql.contains("task_package_hash"));
        assertTrue(runnerSql.contains("'READY', 'LEASED', 'RUNNING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_FINAL'"));
        assertFalse(runnerSql.contains("'OFFERED'"));

        String reliabilitySql = Files.readString(migrations.get(3), StandardCharsets.UTF_8);
        String eventBlock = reliabilitySql.substring(reliabilitySql.indexOf("create table aden_event"),
                reliabilitySql.indexOf("create table aden_outbox"));
        String outboxBlock = reliabilitySql.substring(reliabilitySql.indexOf("create table aden_outbox"),
                reliabilitySql.indexOf("create table aden_inbox"));
        assertFalse(eventBlock.contains("consumer varchar"));
        assertTrue(outboxBlock.contains("consumer varchar"));
        assertTrue(outboxBlock.contains("uq_aden_outbox_event_consumer (workspace_id, event_id, consumer)"));
    }

    @Test
    void permissionsAreIdempotentDefinitionsWithoutRoleGrantOrBusinessSeed() throws IOException {
        String sql = Files.readString(workspacePath("ruoyi-backend", "sql", "aden-permissions.sql"),
                StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        assertEquals(15, Pattern.compile("'aden:[a-z]+:[a-z]+'").matcher(sql).results().count());
        assertFalse(sql.contains("insert into sys_role_menu"));
        assertFalse(sql.contains("insert into aden_workspace"));
        assertTrue(sql.contains("not exists"));
    }

    @Test
    void normalApplicationDisablesImplicitMigrationAndLoopbackProfilesAreExplicit() throws IOException {
        String application = Files.readString(workspacePath("ruoyi-backend", "ruoyi-admin", "src", "main",
                "resources", "application.yml"), StandardCharsets.UTF_8);
        String local = Files.readString(workspacePath("ruoyi-backend", "ruoyi-admin", "src", "main",
                "resources", "application-local.yml"), StandardCharsets.UTF_8);
        String test = Files.readString(workspacePath("ruoyi-backend", "ruoyi-admin", "src", "main",
                "resources", "application-test.yml"), StandardCharsets.UTF_8);
        String guard = Files.readString(workspacePath("ruoyi-backend", "ruoyi-aden", "src", "main", "java",
                "com", "ruoyi", "aden", "migration", "AdenSchemaGuard.java"), StandardCharsets.UTF_8);

        assertTrue(Pattern.compile("(?s)spring:.*?flyway:\\s*\\R\\s+enabled:\\s*false").matcher(application).find());
        assertTrue(application.contains("enabled: ${ADEN_ENABLED:true}"));
        assertTrue(local.contains("address: 127.0.0.1"));
        assertTrue(test.contains("address: 127.0.0.1"));
        assertTrue(guard.contains("flyway.validate()"));
        assertFalse(guard.contains("flyway.migrate()"));
        assertFalse(guard.contains("flyway.baseline()"));
    }

    private static Path migrationDirectory() {
        return workspacePath("ruoyi-backend", "ruoyi-aden", "src", "main", "resources", "db", "aden-migration");
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
