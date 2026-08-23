package com.aiinterviewcoach.adapters.outbound.persistence.governance;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport;
import com.aiinterviewcoach.application.governance.port.ConsentRepository;
import com.aiinterviewcoach.domain.governance.ConsentAction;
import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.governance.ConsentRecord;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;
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

@Repository
@Transactional(readOnly = true)
public class JdbcConsentRepository implements ConsentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcConsentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ConsentRecord record) {
        jdbc.update("""
                insert into governance.consent_record (
                    tenant_id, consent_record_id, user_id,
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
                .addValue("userId", record.userId().value())
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
                .addValue("userId", userId.value())
                .addValue("purpose", purpose.name());
        if (at != null) {
            parameters.addValue("at", JdbcPersistenceSupport.writeInstant(at));
        }
        return jdbc.query("""
                select * from governance.consent_record
                 where tenant_id = :tenantId and user_id = :userId and purpose = :purpose
                """ + timeClause + " order by effective_at, consent_record_id", parameters, mapper());
    }

    private Optional<ConsentRecord> terminal(List<ConsentRecord> history) {
        Set<ResourceId> superseded = new HashSet<>();
        history.forEach(record -> record.supersededConsentId().ifPresent(superseded::add));
        List<ConsentRecord> terminal = history.stream()
                .filter(record -> !superseded.contains(record.id()))
                .toList();
        // 冲突分支安全默认拒绝，不用随机 ID 或时间相同的排序打破平局。
        return terminal.size() == 1 ? Optional.of(terminal.getFirst()) : Optional.empty();
    }

    private RowMapper<ConsentRecord> mapper() {
        return (resultSet, rowNum) -> {
            String supersedes = resultSet.getString("supersedes_id");
            return new ConsentRecord(
                    ResourceId.of(resultSet.getString("consent_record_id")),
                    TenantId.of(resultSet.getString("tenant_id")),
                    UserId.of(resultSet.getString("user_id")),
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
}
