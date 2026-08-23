package com.aiinterviewcoach.adapters.outbound.persistence.identity;

import com.aiinterviewcoach.application.interview.internal.ProfileAccessPort;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JdbcProfileAccessRepository implements ProfileAccessPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProfileAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean belongsTo(TenantId tenantId, UserId userId, ImmutableVersionRef profileVersion) {
        Boolean exists = jdbc.queryForObject("""
                select exists (
                    select 1 from identity.profile_version
                     where tenant_id = :tenantId and user_id = :userId
                       and profile_version_id = :profileId and version_no = :versionNo
                       and content_hash = :contentHash
                )
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("userId", userId.value())
                .addValue("profileId", profileVersion.resourceId().value())
                .addValue("versionNo", profileVersion.versionNo())
                .addValue("contentHash", profileVersion.contentHash()), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
