package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DurableStreamEvent;
import com.ruoyi.interview.domain.platform.DurableStreamType;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** PostgreSQL stream head、append-only event、cursor 校验与有界 replay 的唯一 JDBC owner。 */
@InterviewEnabled
@Repository
@Transactional(transactionManager = "interviewTransactionManager", readOnly = true)
public class JdbcDurableStreamRepository implements DurableStreamPort {

    private static final int MAX_REPLAY_LIMIT = 500;
    private static final int MAX_PURGE_LIMIT = 10_000;
    private static final String EVENT_COLUMNS = """
            tenant_id, stream_type, stream_id, sequence, event_id,
            aggregate_id, aggregate_version, event_type, occurred_at,
            schema_version, correlation_id, event_data, expires_at
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PersistenceJsonCodec json;
    private final StreamCursorCodec cursors = new StreamCursorCodec();

    public JdbcDurableStreamRepository(NamedParameterJdbcTemplate jdbc, PersistenceJsonCodec json) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.json = java.util.Objects.requireNonNull(json);
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public DurableStreamEvent append(AppendCommand command) {
        MapSqlParameterSource parameters = parameters(command);
        jdbc.update("""
                insert into platform.stream_head (
                    tenant_id, stream_type, stream_id, latest_sequence,
                    latest_event_id, retained_from_sequence, updated_at
                ) values (
                    :tenantId, :streamType, :streamId, 0, null, 1, :occurredAt
                ) on conflict do nothing
                """, parameters);

        // 同一 stream 的 append/retention 串行化；sequence 绝不由 max(sequence)+1 推导。
        StreamHead head = lockHead(command.tenantId(), command.streamType(), command.streamId());
        Optional<DurableStreamEvent> existing = findByEventId(command.tenantId(), command.eventId());
        if (existing.isPresent()) {
            DurableStreamEvent event = existing.orElseThrow();
            if (!sameImmutableEvent(parameters)) {
                throw new DataIntegrityViolationException("immutable durable stream event id collision");
            }
            return event;
        }

        Long sequence = jdbc.queryForObject("""
                update platform.stream_head
                   set latest_sequence = latest_sequence + 1,
                       latest_event_id = :eventId,
                       updated_at = greatest(updated_at, :occurredAt)
                 where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                   and latest_sequence = :latestSequence
                returning latest_sequence
                """, parameters.addValue("latestSequence", head.latestSequence()), Long.class);
        if (sequence == null || sequence <= 0) {
            throw new DataIntegrityViolationException("durable stream sequence allocation failed");
        }
        parameters.addValue("sequence", sequence);
        int inserted = jdbc.update("""
                insert into platform.stream_event (
                    tenant_id, stream_type, stream_id, sequence, event_id,
                    aggregate_id, aggregate_version, event_type, occurred_at,
                    schema_version, correlation_id, event_data, expires_at
                ) values (
                    :tenantId, :streamType, :streamId, :sequence, :eventId,
                    :aggregateId, :aggregateVersion, :eventType, :occurredAt,
                    :schemaVersion, :correlationId, cast(:eventData as jsonb), :expiresAt
                )
                """, parameters);
        if (inserted != 1) {
            throw new DataIntegrityViolationException("durable stream event was not appended");
        }
        return toEvent(command, sequence);
    }

    @Override
    public Optional<String> currentCursor(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId
    ) {
        List<StreamHead> heads = jdbc.query("""
                select h.tenant_id, h.stream_type, h.stream_id, h.latest_sequence,
                       h.latest_event_id, h.retained_from_sequence
                  from platform.stream_head h
                  join platform.stream_event e
                    on e.tenant_id = h.tenant_id and e.stream_type = h.stream_type
                   and e.stream_id = h.stream_id and e.sequence = h.latest_sequence
                 where h.tenant_id = :tenantId and h.stream_type = :streamType
                   and h.stream_id = :streamId and h.latest_sequence > 0
                   and e.expires_at > current_timestamp
                """, streamParameters(tenantId, streamType, streamId),
                (row, rowNum) -> mapHead(row));
        if (heads.size() > 1) {
            throw new DataIntegrityViolationException("durable stream head is not unique");
        }
        return heads.stream().findFirst().map(head ->
                cursors.encode(head.latestSequence(), head.latestEventId().orElseThrow()));
    }

    @Override
    public ReplayResult replayAfter(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId,
            String cursor,
            int limit,
            Instant observedAt
    ) {
        java.util.Objects.requireNonNull(tenantId, "tenantId");
        java.util.Objects.requireNonNull(streamType, "streamType");
        java.util.Objects.requireNonNull(streamId, "streamId");
        java.util.Objects.requireNonNull(observedAt, "observedAt");
        if (limit <= 0 || limit > MAX_REPLAY_LIMIT) {
            throw new IllegalArgumentException("replay limit must be between 1 and " + MAX_REPLAY_LIMIT);
        }
        Optional<StreamCursorCodec.Decoded> decoded = cursors.decode(cursor);
        if (decoded.isEmpty()) {
            return ReplayResult.unknownOrForeign();
        }
        Optional<StreamHead> maybeHead = findHead(tenantId, streamType, streamId);
        if (maybeHead.isEmpty()) {
            return ReplayResult.unknownOrForeign();
        }
        StreamHead head = maybeHead.orElseThrow();
        StreamCursorCodec.Decoded anchor = decoded.orElseThrow();
        if (anchor.sequence() < head.retainedFromSequence()) {
            return ReplayResult.expired(streamType, streamId);
        }
        if (anchor.sequence() > head.latestSequence()) {
            return ReplayResult.unknownOrForeign();
        }

        Optional<DurableStreamEvent> anchoredEvent = findBySequence(
                tenantId, streamType, streamId, anchor.sequence());
        if (anchoredEvent.isEmpty()) {
            return anchor.sequence() < head.retainedFromSequence()
                    ? ReplayResult.expired(streamType, streamId)
                    : ReplayResult.unknownOrForeign();
        }
        DurableStreamEvent anchored = anchoredEvent.orElseThrow();
        if (!anchored.eventId().equals(anchor.eventId())) {
            return ReplayResult.unknownOrForeign();
        }
        if (!anchored.expiresAt().isAfter(observedAt)) {
            return ReplayResult.expired(streamType, streamId);
        }

        MapSqlParameterSource parameters = streamParameters(tenantId, streamType, streamId)
                .addValue("afterSequence", anchor.sequence())
                .addValue("observedAt", JdbcPersistenceSupport.writeInstant(observedAt))
                .addValue("limit", limit + 1);
        List<DurableStreamEvent> events = jdbc.query("""
                select %s
                  from platform.stream_event
                 where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                   and sequence > :afterSequence
                 order by sequence
                 limit :limit
                """.formatted(EVENT_COLUMNS), parameters, (row, rowNum) -> mapEvent(row));
        // 不能跳过过期的中间事件后继续发送更新事件，否则浏览器会得到不可恢复的 sequence gap。
        if (events.stream().anyMatch(event -> !event.expiresAt().isAfter(observedAt))) {
            return ReplayResult.expired(streamType, streamId);
        }
        boolean hasMore = events.size() > limit;
        List<ReplayEvent> replay = events.stream().limit(limit)
                .map(event -> new ReplayEvent(event, cursors.encode(event.sequence(), event.eventId())))
                .toList();
        String nextCursor = replay.isEmpty()
                ? cursors.encode(anchor.sequence(), anchor.eventId())
                : replay.get(replay.size() - 1).cursor();
        return ReplayResult.valid(replay, nextCursor, hasMore);
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public int purgeExpiredPrefixes(Instant observedAt, int limit) {
        java.util.Objects.requireNonNull(observedAt, "observedAt");
        if (limit <= 0 || limit > MAX_PURGE_LIMIT) {
            throw new IllegalArgumentException("purge limit must be between 1 and " + MAX_PURGE_LIMIT);
        }
        List<StreamHead> candidates = jdbc.query("""
                select h.tenant_id, h.stream_type, h.stream_id, h.latest_sequence,
                       h.latest_event_id, h.retained_from_sequence
                  from platform.stream_head h
                 where exists (
                       select 1 from platform.stream_event e
                        where e.tenant_id = h.tenant_id and e.stream_type = h.stream_type
                          and e.stream_id = h.stream_id
                          and e.sequence = h.retained_from_sequence
                          and e.expires_at <= :observedAt
                 )
                 order by h.updated_at, h.tenant_id, h.stream_type, h.stream_id
                 limit :limit
                 for update of h skip locked
                """, new MapSqlParameterSource().addValue("observedAt", JdbcPersistenceSupport.writeInstant(observedAt))
                .addValue("limit", limit), (row, rowNum) -> mapHead(row));

        int remaining = limit;
        int deleted = 0;
        for (StreamHead head : candidates) {
            if (remaining == 0) {
                break;
            }
            MapSqlParameterSource parameters = streamParameters(
                    head.tenantId(), head.streamType(), head.streamId())
                    .addValue("retainedFrom", head.retainedFromSequence())
                    .addValue("limit", remaining);
            List<ExpiryRow> rows = jdbc.query("""
                    select sequence, expires_at
                      from platform.stream_event
                     where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                       and sequence >= :retainedFrom
                     order by sequence
                     limit :limit
                     for update
                    """, parameters, (row, rowNum) -> new ExpiryRow(
                    row.getLong("sequence"), JdbcPersistenceSupport.readInstant(row, "expires_at")));
            List<Long> prefix = new ArrayList<>();
            long expected = head.retainedFromSequence();
            for (ExpiryRow row : rows) {
                if (row.sequence() != expected || row.expiresAt().isAfter(observedAt)) {
                    break;
                }
                prefix.add(row.sequence());
                expected++;
            }
            if (prefix.isEmpty()) {
                continue;
            }
            long through = prefix.get(prefix.size() - 1);
            parameters.addValue("throughSequence", through)
                    .addValue("observedAt", JdbcPersistenceSupport.writeInstant(observedAt));
            int removed = jdbc.update("""
                    delete from platform.stream_event
                     where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                       and sequence between :retainedFrom and :throughSequence
                       and expires_at <= :observedAt
                    """, parameters);
            if (removed != prefix.size()) {
                throw new DataIntegrityViolationException("durable stream retention prefix changed concurrently");
            }
            int advanced = jdbc.update("""
                    update platform.stream_head
                       set retained_from_sequence = :throughSequence + 1,
                           updated_at = greatest(updated_at, :observedAt)
                     where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                       and retained_from_sequence = :retainedFrom
                    """, parameters);
            if (advanced != 1) {
                throw new DataIntegrityViolationException("durable stream retention floor did not advance");
            }
            deleted += removed;
            remaining -= removed;
        }
        return deleted;
    }

    private StreamHead lockHead(TenantId tenantId, DurableStreamType streamType, ResourceId streamId) {
        List<StreamHead> heads = jdbc.query("""
                select tenant_id, stream_type, stream_id, latest_sequence,
                       latest_event_id, retained_from_sequence
                  from platform.stream_head
                 where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                 for update
                """, streamParameters(tenantId, streamType, streamId),
                (row, rowNum) -> mapHead(row));
        if (heads.size() != 1) {
            throw new DataIntegrityViolationException("durable stream head could not be locked");
        }
        return heads.get(0);
    }

    private Optional<StreamHead> findHead(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId
    ) {
        return jdbc.query("""
                select tenant_id, stream_type, stream_id, latest_sequence,
                       latest_event_id, retained_from_sequence
                  from platform.stream_head
                 where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                """, streamParameters(tenantId, streamType, streamId),
                (row, rowNum) -> mapHead(row)).stream().findFirst();
    }

    private Optional<DurableStreamEvent> findByEventId(TenantId tenantId, ResourceId eventId) {
        return jdbc.query("select %s from platform.stream_event where tenant_id = :tenantId and event_id = :eventId"
                        .formatted(EVENT_COLUMNS),
                Map.of("tenantId", tenantId.value(), "eventId", eventId.value()),
                (row, rowNum) -> mapEvent(row)).stream().findFirst();
    }

    private boolean sameImmutableEvent(MapSqlParameterSource parameters) {
        Boolean identical = jdbc.queryForObject("""
                select stream_type = :streamType and stream_id = :streamId
                       and aggregate_id = :aggregateId and aggregate_version = :aggregateVersion
                       and event_type = :eventType and occurred_at = :occurredAt
                       and schema_version = :schemaVersion and correlation_id = :correlationId
                       and event_data = cast(:eventData as jsonb) and expires_at = :expiresAt
                  from platform.stream_event
                 where tenant_id = :tenantId and event_id = :eventId
                """, parameters, Boolean.class);
        return Boolean.TRUE.equals(identical);
    }

    private Optional<DurableStreamEvent> findBySequence(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId,
            long sequence
    ) {
        return jdbc.query("""
                select %s from platform.stream_event
                 where tenant_id = :tenantId and stream_type = :streamType and stream_id = :streamId
                   and sequence = :sequence
                """.formatted(EVENT_COLUMNS), streamParameters(tenantId, streamType, streamId)
                .addValue("sequence", sequence), (row, rowNum) -> mapEvent(row)).stream().findFirst();
    }

    private DurableStreamEvent mapEvent(ResultSet row) throws SQLException {
        return new DurableStreamEvent(TenantId.of(row.getString("tenant_id")),
                DurableStreamType.valueOf(row.getString("stream_type")),
                ResourceId.of(row.getString("stream_id")), ResourceId.of(row.getString("event_id")),
                ResourceId.of(row.getString("aggregate_id")),
                new AggregateVersion(row.getLong("aggregate_version")), row.getLong("sequence"),
                row.getString("event_type"), JdbcPersistenceSupport.readInstant(row, "occurred_at"),
                row.getInt("schema_version"), new CorrelationId(row.getString("correlation_id")),
                json.readStringMap(row.getString("event_data")),
                JdbcPersistenceSupport.readInstant(row, "expires_at"));
    }

    private static StreamHead mapHead(ResultSet row) throws SQLException {
        String latestEventId = row.getString("latest_event_id");
        return new StreamHead(TenantId.of(row.getString("tenant_id")),
                DurableStreamType.valueOf(row.getString("stream_type")),
                ResourceId.of(row.getString("stream_id")), row.getLong("latest_sequence"),
                latestEventId == null ? Optional.empty() : Optional.of(ResourceId.of(latestEventId)),
                row.getLong("retained_from_sequence"));
    }

    private MapSqlParameterSource parameters(AppendCommand command) {
        return streamParameters(command.tenantId(), command.streamType(), command.streamId())
                .addValue("eventId", command.eventId().value())
                .addValue("aggregateId", command.aggregateId().value())
                .addValue("aggregateVersion", command.aggregateVersion().value())
                .addValue("eventType", command.type())
                .addValue("occurredAt", JdbcPersistenceSupport.writeInstant(command.occurredAt()))
                .addValue("schemaVersion", command.schemaVersion())
                .addValue("correlationId", command.correlationId().value())
                .addValue("eventData", json.write(command.data()))
                .addValue("expiresAt", JdbcPersistenceSupport.writeInstant(command.expiresAt()));
    }

    private static MapSqlParameterSource streamParameters(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId
    ) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId.value())
                .addValue("streamType", streamType.name()).addValue("streamId", streamId.value());
    }

    private static DurableStreamEvent toEvent(AppendCommand command, long sequence) {
        return new DurableStreamEvent(command.tenantId(), command.streamType(), command.streamId(),
                command.eventId(), command.aggregateId(), command.aggregateVersion(), sequence,
                command.type(), command.occurredAt(), command.schemaVersion(), command.correlationId(),
                command.data(), command.expiresAt());
    }

    private record StreamHead(
            TenantId tenantId,
            DurableStreamType streamType,
            ResourceId streamId,
            long latestSequence,
            Optional<ResourceId> latestEventId,
            long retainedFromSequence
    ) { }

    private record ExpiryRow(long sequence, Instant expiresAt) { }
}

