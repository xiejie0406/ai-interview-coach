package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.application.platform.port.IdempotencyPort;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.IdempotencyKey;
import com.ruoyi.interview.domain.platform.IdempotencyRecord;
import com.ruoyi.interview.domain.platform.IdempotencyState;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@InterviewEnabled
@Repository
@Transactional(transactionManager = "interviewTransactionManager", readOnly = true)
public class JdbcIdempotencyRepository implements IdempotencyPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final PersistenceJsonCodec json;

    public JdbcIdempotencyRepository(NamedParameterJdbcTemplate jdbc, PersistenceJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<IdempotencyRecord> find(
            TenantId tenantId,
            String principalRefHash,
            String operation,
            IdempotencyKey key
    ) {
        return jdbc.query("""
                select * from platform.idempotency_record
                 where tenant_id = :tenantId and principal_ref_hash = :principalHash
                   and operation = :operation and idempotency_key = :idempotencyKey
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("principalHash", principalRefHash)
                .addValue("operation", operation)
                .addValue("idempotencyKey", key.value()), mapper()).stream().findFirst();
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public boolean claim(IdempotencyRecord record) {
        return jdbc.update("""
                insert into platform.idempotency_record (
                    tenant_id, idempotency_record_id, principal_ref_hash, operation,
                    idempotency_key, request_hash, expires_at, state, resource_references,
                    response_status, error_code, aggregate_version
                ) values (
                    :tenantId, :recordId, :principalHash, :operation,
                    :idempotencyKey, :requestHash, :expiresAt, :state, cast(:references as jsonb),
                    :responseStatus, :errorCode, :version
                ) on conflict (tenant_id, principal_ref_hash, operation, idempotency_key) do nothing
                """, parameters(record)) == 1;
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public void save(IdempotencyRecord record) {
        MapSqlParameterSource parameters = parameters(record);
        if (record.version().value() == 0) {
            int inserted = jdbc.update("""
                    insert into platform.idempotency_record (
                        tenant_id, idempotency_record_id, principal_ref_hash, operation,
                        idempotency_key, request_hash, expires_at, state, resource_references,
                        response_status, error_code, aggregate_version
                    ) values (
                        :tenantId, :recordId, :principalHash, :operation,
                        :idempotencyKey, :requestHash, :expiresAt, :state, cast(:references as jsonb),
                        :responseStatus, :errorCode, :version
                    ) on conflict do nothing
                    """, parameters);
            if (inserted == 1) {
                return;
            }
            Boolean identicalClaim = jdbc.queryForObject("""
                    select idempotency_record_id = :recordId and request_hash = :requestHash
                           and state = 'PROCESSING' and aggregate_version = 0
                      from platform.idempotency_record
                     where tenant_id = :tenantId and principal_ref_hash = :principalHash
                       and operation = :operation and idempotency_key = :idempotencyKey
                    """, parameters, Boolean.class);
            if (Boolean.TRUE.equals(identicalClaim)) {
                return;
            }
            throw new DataIntegrityViolationException("idempotency initial claim collision");
        }
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into platform.idempotency_record (
                    tenant_id, idempotency_record_id, principal_ref_hash, operation,
                    idempotency_key, request_hash, expires_at, state, resource_references,
                    response_status, error_code, aggregate_version
                ) values (
                    :tenantId, :recordId, :principalHash, :operation,
                    :idempotencyKey, :requestHash, :expiresAt, :state, cast(:references as jsonb),
                    :responseStatus, :errorCode, :version
                ) on conflict do nothing
                """, """
                update platform.idempotency_record
                   set state = :state, resource_references = cast(:references as jsonb),
                       response_status = :responseStatus, error_code = :errorCode,
                       aggregate_version = :version
                 where tenant_id = :tenantId and idempotency_record_id = :recordId
                   and aggregate_version = :expectedVersion
                """, parameters, record.version().value(),
                "idempotency record " + record.tenantId().value() + "/" + record.id().value());
    }

    private MapSqlParameterSource parameters(IdempotencyRecord record) {
        return new MapSqlParameterSource()
                .addValue("tenantId", record.tenantId().value())
                .addValue("recordId", record.id().value())
                .addValue("principalHash", record.principalRefHash())
                .addValue("operation", record.operation())
                .addValue("idempotencyKey", record.idempotencyKey().value())
                .addValue("requestHash", record.requestHash())
                .addValue("expiresAt", com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport
                        .writeInstant(record.expiresAt()))
                .addValue("state", record.state().name())
                .addValue("references", json.write(record.resourceReferences()))
                .addValue("responseStatus", record.responseStatus().orElse(null))
                .addValue("errorCode", record.errorCode().orElse(null))
                .addValue("version", record.version().value());
    }

    private RowMapper<IdempotencyRecord> mapper() {
        return (row, rowNum) -> IdempotencyRecord.rehydrate(
                ResourceId.of(row.getString("idempotency_record_id")),
                TenantId.of(row.getString("tenant_id")), row.getString("principal_ref_hash"),
                row.getString("operation"), new IdempotencyKey(row.getString("idempotency_key")),
                row.getString("request_hash"),
                JdbcPersistenceSupport.readInstant(row, "expires_at"),
                IdempotencyState.valueOf(row.getString("state")),
                json.readStringMap(row.getString("resource_references")),
                (Integer) row.getObject("response_status"), row.getString("error_code"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }
}

