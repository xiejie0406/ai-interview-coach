package com.ruoyi.interview.infrastructure.persistence.catalog;

import com.ruoyi.interview.application.catalog.port.VerifiedContentSourcePort;
import com.ruoyi.interview.domain.catalog.ContentSourceReference;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/**
 * 从 PostgreSQL catalog source registry 解析已验证来源。
 *
 * <p>请求只携带 {@code content_source_version_id}，verified 状态、许可、来源正文哈希和
 * verification fact 均由服务端 registry 决定。根来源和版本必须同时处于 VERIFIED，且根的
 * current_version_id 必须指向请求版本；任何缺失/脏数据都拒绝作为发布依据。</p>
 */
public final class JdbcVerifiedContentSourceAdapter implements VerifiedContentSourcePort {

    private static final String CAPABILITY = "catalog.verified-content-source-registry";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcVerifiedContentSourceAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<ContentSourceReference> findVerified(
            TenantId tenantId,
            ResourceId contentSourceVersionId
    ) {
        java.util.Objects.requireNonNull(tenantId, "tenantId");
        java.util.Objects.requireNonNull(contentSourceVersionId, "contentSourceVersionId");
        try {
            return jdbc.query("""
                    select s.content_source_id,
                           s.current_version_id,
                           v.content_source_version_id,
                           v.version_no,
                           v.content_hash,
                           v.license_code,
                           v.verification_fact_id,
                           v.verified_at
                      from catalog.content_source s
                      join catalog.content_source_version v
                        on v.tenant_id = s.tenant_id
                       and v.content_source_id = s.content_source_id
                     where s.tenant_id = :tenantId
                       and v.content_source_version_id = :sourceVersionId
                       and s.status = 'VERIFIED'
                       and v.status = 'VERIFIED'
                       and s.current_version_id = v.content_source_version_id
                       and v.verified_at is not null
                    """, Map.of("tenantId", tenantId.value(),
                            "sourceVersionId", contentSourceVersionId.value()),
                    (row, rowNum) -> mapVerified(row)).stream().findFirst();
        } catch (DataAccessException exception) {
            // 未执行 V9 或业务库不可用时不返回伪造来源；由统一错误处理映射为稳定能力不可用。
            throw new AdapterUnavailableException(CAPABILITY);
        }
    }

    private static ContentSourceReference mapVerified(ResultSet row) throws SQLException {
        String sourceId = required(row.getString("content_source_id"), "content_source_id");
        String currentVersionId = required(row.getString("current_version_id"), "current_version_id");
        String versionId = required(row.getString("content_source_version_id"), "content_source_version_id");
        if (!currentVersionId.equals(versionId)) {
            throw new IllegalStateException("verified source current version pointer is inconsistent");
        }
        int versionNo = row.getInt("version_no");
        if (row.wasNull() || versionNo < 1) {
            throw new IllegalStateException("verified source version number is invalid");
        }
        String contentHash = required(row.getString("content_hash"), "content_hash");
        String licenseCode = required(row.getString("license_code"), "license_code");
        String verificationFactId = required(row.getString("verification_fact_id"), "verification_fact_id");
        java.time.Instant verifiedAt = com.ruoyi.interview.infrastructure.persistence.shared
                .JdbcPersistenceSupport.readInstant(row, "verified_at");
        return new ContentSourceReference(
                ResourceId.of(sourceId),
                new ImmutableVersionRef(ResourceId.of(versionId), versionNo, contentHash),
                licenseCode,
                ResourceId.of(verificationFactId),
                verifiedAt);
    }

    private static String required(String value, String column) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("verified source " + column + " is missing");
        }
        return value;
    }
}
