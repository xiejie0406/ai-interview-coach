package com.ruoyi.interview.infrastructure.persistence.governance;

import com.ruoyi.interview.application.governance.port.ConsentPolicyRegistryPort;
import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Tenant-local immutable policy reference registry；正文和 URL 不进入业务数据库。 */
@Transactional(readOnly = true)
@Repository
public class JdbcConsentPolicyRegistryRepository implements ConsentPolicyRegistryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcConsentPolicyRegistryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureRegistered(TenantId tenantId, ConsentPurpose purpose,
                                 ImmutableVersionRef policyVersion, Instant effectiveFrom) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("policyId", policyVersion.resourceId().value())
                .addValue("versionNo", policyVersion.versionNo())
                .addValue("contentHash", policyVersion.contentHash())
                .addValue("purpose", purpose.name())
                .addValue("effectiveFrom", com.ruoyi.interview.infrastructure.persistence.shared
                        .JdbcPersistenceSupport.writeInstant(effectiveFrom));
        int inserted = jdbc.update("""
                insert into governance.consent_policy_version (
                    tenant_id, policy_version_id, version_no, content_hash, purpose, effective_from
                ) values (
                    :tenantId, :policyId, :versionNo, :contentHash, :purpose, :effectiveFrom
                ) on conflict do nothing
                """, parameters);
        if (inserted == 1) {
            return;
        }
        Boolean identical = jdbc.queryForObject("""
                select version_no = :versionNo and content_hash = :contentHash and purpose = :purpose
                  from governance.consent_policy_version
                 where tenant_id = :tenantId and policy_version_id = :policyId
                """, parameters, Boolean.class);
        if (!Boolean.TRUE.equals(identical)) {
            throw new DataIntegrityViolationException("immutable consent policy reference collision");
        }
    }
}

