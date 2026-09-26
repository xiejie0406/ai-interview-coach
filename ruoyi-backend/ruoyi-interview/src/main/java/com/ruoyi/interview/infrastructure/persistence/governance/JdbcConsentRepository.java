package com.ruoyi.interview.infrastructure.persistence.governance;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.application.governance.port.ConsentRepository;
import com.ruoyi.interview.domain.governance.ConsentAction;
import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.governance.ConsentRecord;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@InterviewEnabled
@Repository
@Transactional(transactionManager = "interviewTransactionManager", readOnly = true)
public class JdbcConsentRepository implements ConsentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcConsentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public void append(ConsentRecord record) {
        jdbc.update("""
                insert into governance.consent_record (
                    tenant_id, consent_record_id, ruoyi_user_id,
                    policy_version_id, policy_version_no, policy_content_hash,
                    purpose, action, source, effective_at, supersedes_id
                ) values (
                    :tenantId, :recordId, :userId,
                    :policyId, :policyVersionNo, :policyHash,
                    :purpose, :action, :source, :effectiveAt, :supersedesId
                )
                """, new MapSqlParameterSource()
                .addValue("tenantId", record.tenantId().value())
                .addValue("recordId", record.id().value())
                .addValue("userId", ruoyiUserId(record.userId()))
                .addValue("policyId", record.policyVersion().resourceId().value())
                .addValue("policyVersionNo", record.policyVersion().versionNo())
                .addValue("policyHash", record.policyVersion().contentHash())
                .addValue("purpose", record.purpose().name())
                .addValue("action", record.action().name())
                .addValue("source", record.source())
                .addValue("effectiveAt", JdbcPersistenceSupport.writeInstant(record.effectiveAt()))
                .addValue("supersedesId", record.supersededConsentId()
                        .map(ResourceId::value).orElse(null)));
    }

    @Override
    public List<ConsentRecord> history(TenantId tenantId, UserId userId, ConsentPurpose purpose) {
        return historyAt(tenantId, userId, purpose, null);
    }

    @Override
    public Optional<ConsentRecord> current(TenantId tenantId, UserId userId, ConsentPurpose purpose) {
        return terminal(historyAt(tenantId, userId, purpose, Instant.now()));
    }

    private List<ConsentRecord> historyAt(
            TenantId tenantId,
            UserId userId,
            ConsentPurpose purpose,
            Instant at
    ) {
        String timeClause = at == null ? "" : " and effective_at <= :at";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("userId", ruoyiUserId(userId))
                .addValue("purpose", purpose.name());
        if (at != null) {
            parameters.addValue("at", JdbcPersistenceSupport.writeInstant(at));
        }
        return jdbc.query("""
                select * from governance.consent_record
                 where tenant_id = :tenantId and ruoyi_user_id = :userId and purpose = :purpose
                """ + timeClause + " order by effective_at, consent_record_id", parameters, mapper());
    }

    private Optional<ConsentRecord> terminal(List<ConsentRecord> history) {
        Set<ResourceId> superseded = new HashSet<>();
        history.forEach(record -> record.supersededConsentId().ifPresent(superseded::add));
        List<ConsentRecord> terminal = history.stream()
                .filter(record -> !superseded.contains(record.id()))
                .toList();
        // 冲突分支安全默认拒绝，不用随机 ID 或时间相同的排序打破平局。
        return terminal.size() == 1 ? Optional.of(terminal.get(0)) : Optional.empty();
    }

    private RowMapper<ConsentRecord> mapper() {
        return (resultSet, rowNum) -> {
            String supersedes = resultSet.getString("supersedes_id");
            return new ConsentRecord(
                    ResourceId.of(resultSet.getString("consent_record_id")),
                    TenantId.of(resultSet.getString("tenant_id")),
                    UserId.of(Long.toString(resultSet.getLong("ruoyi_user_id"))),
                    new ImmutableVersionRef(
                            ResourceId.of(resultSet.getString("policy_version_id")),
                            resultSet.getInt("policy_version_no"),
                            resultSet.getString("policy_content_hash")),
                    ConsentPurpose.valueOf(resultSet.getString("purpose")),
                    ConsentAction.valueOf(resultSet.getString("action")),
                    resultSet.getString("source"),
                    JdbcPersistenceSupport.readInstant(resultSet, "effective_at"),
                    supersedes == null ? null : ResourceId.of(supersedes));
        };
    }

    private static long ruoyiUserId(UserId userId) {
        return Long.parseLong(userId.value());
    }
}

