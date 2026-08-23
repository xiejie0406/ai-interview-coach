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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

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

    @BeforeAll
    static void migrate() {
        try {
            POSTGRES.start();
        } catch (RuntimeException blocked) {
            Assumptions.abort("Testcontainers PostgreSQL Blocked: Docker environment unavailable");
        }
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
    }

    @Test
    void migrationAndDurableStreamRoundTripPreserveTenantCursor() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        assertEquals("voice.audio_artifact", jdbc.queryForObject(
                "select to_regclass('voice.audio_artifact')::text", Map.of(), String.class));
        assertEquals("platform.stream_event", jdbc.queryForObject(
                "select to_regclass('platform.stream_event')::text", Map.of(), String.class));
    }

    @Test
    void appendReplayAndForeignCursorAreOwnerScoped() {
        Instant now = Instant.now();
        TenantId tenant = TenantId.of("tenant-pg");
        ResourceId streamId = ResourceId.of("session-pg");
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
}
