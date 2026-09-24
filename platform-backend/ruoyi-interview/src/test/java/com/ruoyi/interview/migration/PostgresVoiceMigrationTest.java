package com.ruoyi.interview.migration;

import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DurableStreamType;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.infrastructure.persistence.platform.JdbcDurableStreamRepository;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 从空 PostgreSQL 验证最新迁移，并用真实 JDBC stream repository 做最小 round-trip。 */
class PostgresVoiceMigrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("interview")
            .withUsername("interview")
            .withPassword("interview");

    private static DataSource dataSource;
    private static JdbcDurableStreamRepository streams;
    private static TransactionTemplate transaction;
    private static LocalDatabase localDatabase;

    @BeforeAll
    static void migrate() {
        String jdbcUrl;
        String username;
        String password;
        try {
            POSTGRES.start();
            jdbcUrl = POSTGRES.getJdbcUrl();
            username = POSTGRES.getUsername();
            password = POSTGRES.getPassword();
        } catch (RuntimeException blocked) {
            try {
                localDatabase = LocalDatabase.createFromEnvironment();
            } catch (RuntimeException localFailure) {
                localFailure.addSuppressed(blocked);
                throw localFailure;
            }
            jdbcUrl = localDatabase.jdbcUrl();
            username = localDatabase.username();
            password = localDatabase.password();
        }
        dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .schemas("platform")
                .defaultSchema("platform")
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load()
                .migrate();
        streams = new JdbcDurableStreamRepository(new NamedParameterJdbcTemplate(dataSource), new PersistenceJsonCodec());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void stop() {
        if (POSTGRES.isRunning()) POSTGRES.stop();
        if (localDatabase != null) localDatabase.close();
    }

    @Test
    void migrationAndDurableStreamRoundTripPreserveTenantCursor() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        assertEquals("voice.audio_artifact", jdbc.queryForObject(
                "select to_regclass('voice.audio_artifact')::text", Map.of(), String.class));
        assertEquals("platform.stream_event", jdbc.queryForObject(
                "select to_regclass('platform.stream_event')::text", Map.of(), String.class));
        assertEquals("evaluation.interview_feedback", jdbc.queryForObject(
                "select to_regclass('evaluation.interview_feedback')::text", Map.of(), String.class));
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from platform.flyway_schema_history
                 where version in ('13', '14') and success
                """, Map.of(), Integer.class));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from pg_constraint
                     where conname = 'consent_policy_business_tenant_fk'
                )
                """, Map.of(), Boolean.class)));
    }

    @Test
    void appendReplayAndForeignCursorAreOwnerScoped() {
        Instant now = Instant.now();
        TenantId tenant = TenantId.of("tenant-pg");
        ResourceId streamId = ResourceId.of("session-pg");
        new NamedParameterJdbcTemplate(dataSource).update("""
                insert into platform.business_tenant
                    (tenant_id, owner_ruoyi_user_id, tenant_type, status)
                values (:tenantId, :ownerUserId, 'PERSONAL', 'ACTIVE')
                """, Map.of("tenantId", tenant.value(), "ownerUserId", 1L));
        DurableStreamPort.AppendCommand command = new DurableStreamPort.AppendCommand(
                tenant, DurableStreamType.INTERVIEW, streamId, ResourceId.of("event-pg-1"), streamId,
                new AggregateVersion(1), "interview.question.committed", now, 1,
                new CorrelationId("corr-pg-1"), Map.of("state", "QUESTION_COMMITTED"), now.plus(Duration.ofHours(1)));
        transaction.executeWithoutResult(status -> streams.append(command));
        String cursor = streams.currentCursor(tenant, DurableStreamType.INTERVIEW, streamId).orElseThrow();
        assertTrue(streams.replayAfter(tenant, DurableStreamType.INTERVIEW, streamId, cursor, 100, now)
                .events().isEmpty());
        assertEquals(DurableStreamPort.CursorStatus.UNKNOWN_OR_FOREIGN,
                streams.replayAfter(TenantId.of("tenant-other"), DurableStreamType.INTERVIEW, streamId,
                        cursor, 100, now).status());
    }

    /**
     * Docker 不可用时，只在显式提供的本地 PostgreSQL 实例上创建随机测试库。
     * 测试结束仅删除本次生成且带固定前缀的数据库，绝不迁移或清理业务库。
     */
    private record LocalDatabase(String adminUrl, String jdbcUrl, String username,
                                 String password, String databaseName) implements AutoCloseable {
        private static final String PREFIX = "interview_test_";

        static LocalDatabase createFromEnvironment() {
            String sourceUrl = environment("INTERVIEW_TEST_DB_ADMIN_URL")
                    .or(() -> environment("INTERVIEW_DB_URL"))
                    .orElseThrow(() -> new IllegalStateException(
                            "Docker 不可用；请设置 INTERVIEW_TEST_DB_ADMIN_URL 或 INTERVIEW_DB_URL，"
                                    + "测试将从该实例创建随机临时数据库"));
            String username = environment("INTERVIEW_TEST_DB_USERNAME")
                    .or(() -> environment("INTERVIEW_DB_USERNAME"))
                    .orElseThrow(() -> new IllegalStateException("缺少 PostgreSQL 测试用户名"));
            String password = environment("INTERVIEW_TEST_DB_PASSWORD")
                    .or(() -> environment("INTERVIEW_DB_PASSWORD")).orElse("");
            String databaseName = PREFIX + UUID.randomUUID().toString().replace("-", "");
            String adminUrl = withDatabase(sourceUrl, "postgres");
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.execute("create database " + identifier(databaseName));
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "无法创建 PostgreSQL 临时测试库；连接用户必须拥有 CREATEDB 权限", exception);
            }
            return new LocalDatabase(adminUrl, withDatabase(sourceUrl, databaseName),
                    username, password, databaseName);
        }

        @Override
        public void close() {
            if (!databaseName.startsWith(PREFIX)) {
                throw new IllegalStateException("拒绝删除不属于迁移测试的数据库");
            }
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.execute("drop database " + identifier(databaseName) + " with (force)");
            } catch (SQLException exception) {
                throw new IllegalStateException("无法删除 PostgreSQL 临时测试库 " + databaseName, exception);
            }
        }

        private static Optional<String> environment(String name) {
            return Optional.ofNullable(System.getenv(name)).map(String::trim).filter(value -> !value.isEmpty());
        }

        private static String withDatabase(String jdbcUrl, String databaseName) {
            String prefix = "jdbc:postgresql://";
            if (!jdbcUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("测试数据库 URL 必须使用 jdbc:postgresql:// 格式");
            }
            int queryStart = jdbcUrl.indexOf('?');
            int pathEnd = queryStart < 0 ? jdbcUrl.length() : queryStart;
            int pathStart = jdbcUrl.indexOf('/', prefix.length());
            if (pathStart < 0 || pathStart >= pathEnd - 1) {
                throw new IllegalArgumentException("测试数据库 URL 必须包含数据库名");
            }
            String suffix = queryStart < 0 ? "" : jdbcUrl.substring(queryStart);
            return jdbcUrl.substring(0, pathStart + 1) + databaseName + suffix;
        }

        private static String identifier(String value) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
    }
}
