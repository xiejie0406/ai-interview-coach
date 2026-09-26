package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.Job;
import com.ruoyi.interview.domain.platform.JobState;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
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
@Transactional(transactionManager = "interviewTransactionManager", readOnly = true)
public class JdbcJobRepository implements JobPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final PersistenceJsonCodec json;

    public JdbcJobRepository(NamedParameterJdbcTemplate jdbc, PersistenceJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Job> find(TenantId tenantId, ResourceId jobId) {
        return jdbc.query("""
                select * from platform.job
                 where tenant_id = :tenantId and job_id = :jobId
                """, Map.of("tenantId", tenantId.value(), "jobId", jobId.value()), mapper())
                .stream().findFirst();
    }

    @Override
    public Optional<Job> findByBusinessOperation(
            TenantId tenantId,
            String jobType,
            ResourceId businessOperationId
    ) {
        return jdbc.query("""
                select * from platform.job
                 where tenant_id = :tenantId and job_type = :jobType
                   and business_operation_id = :operationId
                """, Map.of("tenantId", tenantId.value(), "jobType", jobType,
                        "operationId", businessOperationId.value()), mapper()).stream().findFirst();
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public void save(Job job) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", job.tenantId().value())
                .addValue("jobId", job.id().value())
                .addValue("jobType", job.jobType())
                .addValue("operationId", job.businessOperationId().value())
                .addValue("payload", json.write(job.payloadReferences()))
                .addValue("maxAttempts", job.maxAttempts())
                .addValue("state", job.state().name())
                .addValue("availableAt", JdbcPersistenceSupport.writeInstant(job.availableAt()))
                .addValue("leaseOwner", job.leaseOwner().orElse(null))
                .addValue("leaseExpiresAt", job.leaseExpiresAt()
                        .map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("heartbeatAt", job.heartbeatAt()
                        .map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("attemptCount", job.attemptCount())
                .addValue("lastErrorCode", job.lastErrorCode().orElse(null))
                .addValue("version", job.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into platform.job (
                    tenant_id, job_id, job_type, business_operation_id, payload_references,
                    max_attempts, state, available_at, lease_owner, lease_expires_at,
                    heartbeat_at, attempt_count, last_error_code, aggregate_version
                ) values (
                    :tenantId, :jobId, :jobType, :operationId, cast(:payload as jsonb),
                    :maxAttempts, :state, :availableAt, :leaseOwner, :leaseExpiresAt,
                    :heartbeatAt, :attemptCount, :lastErrorCode, :version
                ) on conflict do nothing
                """, """
                update platform.job
                   set state = :state, available_at = :availableAt,
                       lease_owner = :leaseOwner, lease_expires_at = :leaseExpiresAt,
                       heartbeat_at = :heartbeatAt, attempt_count = :attemptCount,
                       last_error_code = :lastErrorCode, aggregate_version = :version
                 where tenant_id = :tenantId and job_id = :jobId
                   and aggregate_version = :expectedVersion
                """, parameters, job.version().value(),
                "job " + job.tenantId().value() + "/" + job.id().value());
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public List<Job> findClaimable(String jobType, Instant availableBefore, int limit) {
        // MANDATORY keeps these row locks alive until the caller claims and saves each Job.
        return jdbc.query("""
                select * from platform.job
                 where job_type = :jobType and state = 'PENDING'
                   and available_at <= :availableBefore and attempt_count < max_attempts
                 order by available_at, tenant_id, job_id
                 limit :limit
                 for update skip locked
                """, new MapSqlParameterSource()
                .addValue("jobType", jobType)
                .addValue("availableBefore", JdbcPersistenceSupport.writeInstant(availableBefore))
                .addValue("limit", limit), mapper());
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public List<Job> findRetryable(Instant availableBefore, int limit) {
        return jdbc.query("""
                select * from platform.job
                 where state = 'FAILED_RETRYABLE' and available_at <= :availableBefore
                   and attempt_count < max_attempts
                 order by available_at, tenant_id, job_id
                 limit :limit
                 for update skip locked
                """, new MapSqlParameterSource()
                .addValue("availableBefore", JdbcPersistenceSupport.writeInstant(availableBefore))
                .addValue("limit", limit), mapper());
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public List<Job> findExpiredRunningLeases(Instant expiredBefore, int limit) {
        return jdbc.query("""
                select * from platform.job
                 where state = 'RUNNING' and lease_expires_at <= :expiredBefore
                 order by lease_expires_at, tenant_id, job_id
                 limit :limit
                 for update skip locked
                """, new MapSqlParameterSource()
                .addValue("expiredBefore", JdbcPersistenceSupport.writeInstant(expiredBefore))
                .addValue("limit", limit), mapper());
    }

    private RowMapper<Job> mapper() {
        return (row, rowNum) -> Job.rehydrate(
                ResourceId.of(row.getString("job_id")), TenantId.of(row.getString("tenant_id")),
                row.getString("job_type"), ResourceId.of(row.getString("business_operation_id")),
                json.readStringMap(row.getString("payload_references")), row.getInt("max_attempts"),
                JobState.valueOf(row.getString("state")),
                JdbcPersistenceSupport.readInstant(row, "available_at"), row.getString("lease_owner"),
                JdbcPersistenceSupport.readNullableInstant(row, "lease_expires_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "heartbeat_at"),
                row.getInt("attempt_count"), row.getString("last_error_code"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }
}

