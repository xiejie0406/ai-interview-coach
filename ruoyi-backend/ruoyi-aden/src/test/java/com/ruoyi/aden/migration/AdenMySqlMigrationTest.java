package com.ruoyi.aden.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 由 run-local-mysql-tests.ps1 提供本机一次性 MySQL 与随机临时库。 */
class AdenMySqlMigrationTest {
    private static DataSource dataSource;
    private static Flyway flyway;
    private static JdbcTemplate jdbc;
    private static String databaseName;
    private static String adminUrl;
    private static String adminUsername;
    private static String adminPassword;
    private static LocalDatabase localDatabase;

    @BeforeAll
    static void baselineAndMigrate() {
        String jdbcUrl;
        String username;
        String password;
        Assumptions.assumeTrue(LocalDatabase.isConfigured(),
                "未配置本机一次性 MySQL；请运行 ruoyi-aden/scripts/run-local-mysql-tests.ps1");
        localDatabase = LocalDatabase.createFromEnvironment();
        jdbcUrl = localDatabase.jdbcUrl();
        username = localDatabase.username();
        password = localDatabase.password();
        databaseName = localDatabase.databaseName();
        adminUrl = localDatabase.adminUrl();
        adminUsername = username;
        adminPassword = password;
        dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        ResourceDatabasePopulator baseline = new ResourceDatabasePopulator();
        baseline.setSqlScriptEncoding("UTF-8");
        baseline.addScript(new FileSystemResource(workspacePath("ruoyi-backend", "sql", "ry_20260417.sql")));
        baseline.execute(dataSource);

        AdenDatabasePreconditions.verify(dataSource, databaseName);
        flyway = AdenFlywayFactory.create(dataSource);
        flyway.baseline();
        assertEquals(4, flyway.migrate().migrationsExecuted);
        flyway.validate();
        AdenSchemaGuard.requireVersion(flyway, "4");
        AdenDatabasePreconditions.verifyCurrentSchema(dataSource, databaseName);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stop() {
        if (localDatabase != null) localDatabase.close();
    }

    @Test
    void createsTwelveBusinessTablesAndDedicatedHistory() {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                 where table_schema = ? and table_name like 'aden\\_%'
                 order by table_name
                """, String.class, databaseName);
        assertEquals(13, tables.size());
        assertTrue(tables.contains("aden_flyway_schema_history"));
        assertEquals(12, tables.stream().filter(name -> !name.equals("aden_flyway_schema_history")).count());
        assertEquals(5, jdbc.queryForObject(
                "select count(*) from aden_flyway_schema_history where success = 1", Integer.class));
        assertEquals(44, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = ? and table_name like 'aden\\_%'
                   and data_type = 'datetime' and datetime_precision = 6
                """, Integer.class, databaseName));
        assertEquals(12, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = ? and table_name like 'aden\\_%'
                   and column_name = 'workspace_id'
                   and character_set_name = 'ascii' and collation_name = 'ascii_bin'
                """, Integer.class, databaseName));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from information_schema.statistics
                 where table_schema = ? and table_name = 'aden_event'
                   and index_name = 'uq_aden_event_sequence' and non_unique = 0
                   and seq_in_index = 1
                """, Integer.class, databaseName));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from information_schema.statistics
                 where table_schema = ? and table_name = 'aden_runner_delivery'
                   and index_name = 'ix_aden_delivery_claim'
                   and seq_in_index = 1
                """, Integer.class, databaseName));
    }

    @Test
    void repeatedMigrateIsNoOpAndValidateSucceeds() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @Test
    void wrongDatabaseAndEmptyDatabaseFailBeforeFlyway() throws SQLException {
        assertThrows(IllegalStateException.class,
                () -> AdenDatabasePreconditions.verify(dataSource, "not_" + databaseName));
        String emptyDatabaseName = "aden_empty_" + UUID.randomUUID().toString().replace("-", "");
        String emptyUrl = withDatabase(adminUrl, emptyDatabaseName);
        try (Connection connection = DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + identifier(emptyDatabaseName));
        }
        try {
            DataSource emptyDatabase = new DriverManagerDataSource(emptyUrl, adminUsername, adminPassword);
            assertThrows(IllegalStateException.class,
                    () -> AdenDatabasePreconditions.verify(emptyDatabase, emptyDatabaseName));
        } finally {
            try (Connection connection = DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
                 Statement statement = connection.createStatement()) {
                statement.execute("drop database " + identifier(emptyDatabaseName));
            }
        }
    }

    @Test
    void checksumTamperingIsDetectedAndRestored() {
        Integer checksum = jdbc.queryForObject(
                "select checksum from aden_flyway_schema_history where version = '4'", Integer.class);
        try {
            jdbc.update("update aden_flyway_schema_history set checksum = checksum + 1 where version = '4'");
            assertThrows(FlywayValidateException.class, flyway::validate);
        } finally {
            jdbc.update("update aden_flyway_schema_history set checksum = ? where version = '4'", checksum);
        }
        flyway.validate();
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

    private static String withDatabase(String jdbcUrl, String databaseName) {
        String prefix = "jdbc:mysql://";
        if (!jdbcUrl.startsWith(prefix)) throw new IllegalArgumentException("测试数据库 URL 必须使用 jdbc:mysql://");
        int queryStart = jdbcUrl.indexOf('?');
        int pathEnd = queryStart < 0 ? jdbcUrl.length() : queryStart;
        int pathStart = jdbcUrl.indexOf('/', prefix.length());
        if (pathStart < 0 || pathStart >= pathEnd) throw new IllegalArgumentException("测试数据库 URL 缺少路径");
        String suffix = queryStart < 0 ? "" : jdbcUrl.substring(queryStart);
        return jdbcUrl.substring(0, pathStart + 1) + databaseName + suffix;
    }

    private static String identifier(String value) {
        if (!value.matches("^aden_(?:test|empty)_[a-f0-9]{32}$")) {
            throw new IllegalArgumentException("拒绝操作非 Aden 随机测试库 " + value);
        }
        return '`' + value + '`';
    }

    private record LocalDatabase(String adminUrl, String jdbcUrl, String username,
                                 String password, String databaseName) implements AutoCloseable {
        static boolean isConfigured() {
            return environment("ADEN_TEST_DB_ADMIN_URL").isPresent();
        }

        static LocalDatabase createFromEnvironment() {
            String sourceUrl = environment("ADEN_TEST_DB_ADMIN_URL")
                    .orElseThrow(() -> new IllegalStateException(
                            "未设置 ADEN_TEST_DB_ADMIN_URL；请运行本机一次性 MySQL 测试脚本"));
            String username = environment("ADEN_TEST_DB_USERNAME")
                    .orElseThrow(() -> new IllegalStateException("缺少 ADEN_TEST_DB_USERNAME"));
            String password = System.getenv().getOrDefault("ADEN_TEST_DB_PASSWORD", "");
            String databaseName = "aden_test_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection connection = DriverManager.getConnection(sourceUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("create database " + identifier(databaseName)
                        + " character set utf8mb4 collate utf8mb4_0900_ai_ci");
            } catch (SQLException exception) {
                throw new IllegalStateException("无法创建 Aden 随机临时测试库", exception);
            }
            return new LocalDatabase(sourceUrl, withDatabase(sourceUrl, databaseName), username, password, databaseName);
        }

        @Override
        public void close() {
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("drop database " + identifier(databaseName));
            } catch (SQLException exception) {
                throw new IllegalStateException("无法删除 Aden 随机临时测试库 " + databaseName, exception);
            }
        }

        private static Optional<String> environment(String name) {
            return Optional.ofNullable(System.getenv(name)).map(String::trim).filter(value -> !value.isEmpty());
        }
    }
}
