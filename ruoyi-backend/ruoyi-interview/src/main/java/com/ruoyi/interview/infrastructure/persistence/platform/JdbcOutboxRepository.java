package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.application.platform.port.OutboxPort;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.OutboxEvent;
import com.ruoyi.interview.domain.platform.OutboxState;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@InterviewEnabled
@Repository
@Transactional(readOnly = true)
public class JdbcOutboxRepository implements OutboxPort {

    private static final String SELECT_JOIN = """
            select e.tenant_id, e.event_id, e.aggregate_type, e.aggregate_id,
                   e.source_aggregate_version, e.event_type, e.schema_version,
                   e.correlation_id, e.payload_references,
                   d.state, d.available_at, d.claimed_by, d.claim_expires_at,
                   d.published_at, d.attempt_count, d.last_error_code,
                   d.aggregate_version as delivery_version
              from platform.outbox_event e
              join platform.outbox_delivery d
                on d.tenant_id = e.tenant_id and d.event_id = e.event_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PersistenceJsonCodec json;

    public JdbcOutboxRepository(NamedParameterJdbcTemplate jdbc, PersistenceJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<OutboxEvent> find(TenantId tenantId, ResourceId eventId) {
        return jdbc.query(SELECT_JOIN + " where e.tenant_id = :tenantId and e.event_id = :eventId",
                Map.of("tenantId", tenantId.value(), "eventId", eventId.value()), mapper())
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OutboxEvent event) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", event.tenantId().value())
                .addValue("eventId", event.id().value())
                .addValue("aggregateType", event.aggregateType())
                .addValue("aggregateId", event.aggregateId().value())
                .addValue("sourceVersion", event.sourceAggregateVersion().value())
                .addValue("eventType", event.eventType())
                .addValue("schemaVersion", event.schemaVersion())
                .addValue("correlationId", event.correlationId().value())
                .addValue("payload", json.write(event.payloadReferences()))
                .addValue("state", event.state().name())
                .addValue("availableAt", com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport.writeInstant(event.availableAt()))
                .addValue("claimedBy", event.claimedBy().orElse(null))
                .addValue("claimExpiresAt", event.claimExpiresAt().map(com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("publishedAt", event.publishedAt().map(com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("attemptCount", event.attemptCount())
                .addValue("lastErrorCode", event.lastErrorCode().orElse(null))
                .addValue("version", event.version().value());
        // 新事件的 availableAt 同时是源事件发生时间；后续 retry/reclaim 只能推进
        // delivery.available_at，immutable event.occurred_at 必须保持首次写入值。
        int eventInserted = jdbc.update("""
                insert into platform.outbox_event (
                    tenant_id, event_id, aggregate_type, aggregate_id, source_aggregate_version,
                    event_type, schema_version, correlation_id, payload_references, occurred_at
                ) values (
                    :tenantId, :eventId, :aggregateType, :aggregateId, :sourceVersion,
                    :eventType, :schemaVersion, :correlationId, cast(:payload as jsonb), :availableAt
                ) on conflict do nothing
                """, parameters);
        if (eventInserted == 0) {
            // OutboxEvent rehydrate 后不暴露原始 occurredAt，因此 immutable collision 只比较
            // 身份和 payload，不能把已经变化的 delivery availableAt 再拿来比较。
            Boolean identical = jdbc.queryForObject("""
                    select aggregate_type = :aggregateType and aggregate_id = :aggregateId
                           and source_aggregate_version = :sourceVersion and event_type = :eventType
                           and schema_version = :schemaVersion and correlation_id = :correlationId
                           and payload_references = cast(:payload as jsonb)
                      from platform.outbox_event
                     where tenant_id = :tenantId and event_id = :eventId
                    """, parameters, Boolean.class);
            if (!Boolean.TRUE.equals(identical)) {
                throw new DataIntegrityViolationException("immutable outbox event id collision");
            }
        }
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into platform.outbox_delivery (
                    tenant_id, event_id, state, available_at, claimed_by, claim_expires_at,
                    published_at, attempt_count, last_error_code, aggregate_version
                ) values (
                    :tenantId, :eventId, :state, :availableAt, :claimedBy, :claimExpiresAt,
                    :publishedAt, :attemptCount, :lastErrorCode, :version
                ) on conflict do nothing
                """, """
                update platform.outbox_delivery
                   set state = :state, available_at = :availableAt,
                       claimed_by = :claimedBy, claim_expires_at = :claimExpiresAt,
                       published_at = :publishedAt, attempt_count = :attemptCount,
                       last_error_code = :lastErrorCode, aggregate_version = :version
                 where tenant_id = :tenantId and event_id = :eventId
                   and aggregate_version = :expectedVersion
                """, parameters, event.version().value(),
                "outbox delivery " + event.tenantId().value() + "/" + event.id().value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxEvent> findPublishable(Instant availableBefore, int limit) {
        // Locks the mutable delivery row while the immutable event payload remains append-only.
        return jdbc.query(SELECT_JOIN + """
                 where d.state in ('PENDING','FAILED_RETRYABLE')
                   and d.available_at <= :availableBefore
                 order by d.available_at, d.tenant_id, d.event_id
                 limit :limit
                 for update of d skip locked
                """, new MapSqlParameterSource()
                .addValue("availableBefore", com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport.writeInstant(availableBefore))
                .addValue("limit", limit), mapper());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxEvent> findExpiredClaims(Instant expiredBefore, int limit) {
        return jdbc.query(SELECT_JOIN + """
                 where d.state = 'CLAIMED' and d.claim_expires_at <= :expiredBefore
                 order by d.claim_expires_at, d.tenant_id, d.event_id
                 limit :limit
                 for update of d skip locked
                """, new MapSqlParameterSource().addValue("expiredBefore",
                        com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport.writeInstant(expiredBefore))
                .addValue("limit", limit), mapper());
    }

    private RowMapper<OutboxEvent> mapper() {
        return (row, rowNum) -> OutboxEvent.rehydrate(
                ResourceId.of(row.getString("event_id")), TenantId.of(row.getString("tenant_id")),
                row.getString("aggregate_type"), ResourceId.of(row.getString("aggregate_id")),
                new AggregateVersion(row.getLong("source_aggregate_version")),
                row.getString("event_type"), row.getInt("schema_version"),
                new CorrelationId(row.getString("correlation_id")),
                json.readStringMap(row.getString("payload_references")),
                OutboxState.valueOf(row.getString("state")),
                JdbcPersistenceSupport.readInstant(row, "available_at"), row.getString("claimed_by"),
                JdbcPersistenceSupport.readNullableInstant(row, "claim_expires_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "published_at"),
                row.getInt("attempt_count"), row.getString("last_error_code"),
                new AggregateVersion(row.getLong("delivery_version")));
    }
}

