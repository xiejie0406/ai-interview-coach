package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.security.BusinessTenantResolver;
import com.ruoyi.interview.application.governance.port.ConsentPolicyRegistryPort;
import com.ruoyi.interview.application.interview.internal.ProfileAccessPort;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.infrastructure.persistence.platform.DomainEventEnvelopePolicy;
import com.ruoyi.interview.infrastructure.persistence.platform.DurableStreamEventPolicy;
import com.ruoyi.interview.infrastructure.persistence.platform.StrictDomainEventEnvelopePolicy;
import com.ruoyi.interview.infrastructure.persistence.platform.StrictDurableStreamEventPolicy;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.persistence.shared.UnavailableSensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.crypto.AesGcmSensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.platform.SystemClockAdapter;
import com.ruoyi.interview.infrastructure.platform.UuidIdGeneratorAdapter;
import com.ruoyi.interview.infrastructure.persistence.platform.SpringTransactionAdapter;
import com.ruoyi.interview.infrastructure.persistence.governance.JdbcConsentPolicyRegistryRepository;
import com.ruoyi.interview.controller.rest.health.ServiceMetadata;
import com.ruoyi.interview.configuration.properties.ProviderProperties;
import com.ruoyi.interview.configuration.properties.VoiceRuntimeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/** ruoyi-interview 的唯一组合根：业务 PostgreSQL、RuoYi principal、Provider 和本地事务边界。 */
@InterviewEnabled
@Configuration
@EnableConfigurationProperties({ProviderProperties.class, VoiceRuntimeProperties.class})
public class InterviewInfrastructureConfiguration {

    @Bean(name = "interviewDataSource")
    public DataSource interviewDataSource(
            @Value("${interview.datasource.url}") String url,
            @Value("${interview.datasource.username}") String username,
            @Value("${interview.datasource.password:}") String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean(name = "interviewTransactionManager")
    public PlatformTransactionManager interviewTransactionManager(
            @Qualifier("interviewDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public NamedParameterJdbcTemplate interviewJdbcTemplate(
            @Qualifier("interviewDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(ConsentPolicyRegistryPort.class)
    public ConsentPolicyRegistryPort interviewConsentPolicyRegistry(
            @Qualifier("interviewJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        return new JdbcConsentPolicyRegistryRepository(jdbc);
    }

    @Bean
    public Clock interviewClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClockPort interviewClockPort(
            @Qualifier("interviewClock") Clock interviewClock) {
        return new SystemClockAdapter(interviewClock);
    }

    @Bean
    public IdGeneratorPort interviewIdGenerator() {
        return new UuidIdGeneratorAdapter(UUID::randomUUID);
    }

    @Bean
    public SensitiveEnvelopeCipher interviewSensitiveEnvelopeCipher(VoiceRuntimeProperties properties) {
        String keyId = properties.getSensitiveEnvelopeKeyId();
        String encodedKey = properties.getSensitiveEnvelopeKeyBase64();
        if (isBlank(keyId) && isBlank(encodedKey)) {
            return new UnavailableSensitiveEnvelopeCipher();
        }
        if (isBlank(keyId) || isBlank(encodedKey)) {
            throw new IllegalStateException("敏感字段信封 key id 与 key material 必须同时配置");
        }
        byte[] key = AesGcmSensitiveEnvelopeCipher.decodeBase64Key(
                encodedKey, "interview.voice-runtime.sensitive-envelope-key-base64");
        String previousId = properties.getPreviousSensitiveEnvelopeKeyId();
        String previousEncoded = properties.getPreviousSensitiveEnvelopeKeyBase64();
        if (isBlank(previousId) != isBlank(previousEncoded)) {
            throw new IllegalStateException("旧敏感字段信封 key id 与 key material 必须同时配置");
        }
        byte[] previousKey = isBlank(previousId) ? null : AesGcmSensitiveEnvelopeCipher.decodeBase64Key(
                previousEncoded, "interview.voice-runtime.previous-sensitive-envelope-key-base64");
        try {
            java.util.Map<String, byte[]> keys = new java.util.HashMap<>();
            keys.put(keyId, key);
            if (previousKey != null) keys.put(previousId, previousKey);
            return new AesGcmSensitiveEnvelopeCipher(keyId, keys, new SecureRandom());
        } finally {
            Arrays.fill(key, (byte) 0);
            if (previousKey != null) Arrays.fill(previousKey, (byte) 0);
        }
    }

    @Bean
    @Primary
    public PersistenceJsonCodec interviewPersistenceJsonCodec() {
        return new PersistenceJsonCodec();
    }

    @Bean
    public ServiceMetadata interviewServiceMetadata(
            @Value("${ruoyi.name:RuoYi}") String serviceName,
            @Value("${ruoyi.version:unknown}") String releaseVersion) {
        return new ServiceMetadata() {
            @Override
            public String serviceName() {
                return serviceName;
            }

            @Override
            public String releaseVersion() {
                return releaseVersion;
            }
        };
    }

    @Bean
    public DomainEventEnvelopePolicy interviewDomainEventEnvelopePolicy() {
        return new StrictDomainEventEnvelopePolicy();
    }

    @Bean
    public DurableStreamEventPolicy interviewDurableStreamEventPolicy() {
        return new StrictDurableStreamEventPolicy(Duration.ofDays(7), Duration.ofDays(7));
    }

    @Bean
    public BusinessTenantResolver interviewBusinessTenantResolver(
            @Qualifier("interviewJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        return userId -> {
            String tenantId = "ruoyi-user-" + userId;
            jdbc.update("""
                    insert into platform.business_tenant
                        (tenant_id, owner_ruoyi_user_id, tenant_type, status)
                    values (:tenantId, :userId, 'PERSONAL', 'ACTIVE')
                    on conflict (tenant_id) do nothing
                    """, java.util.Map.of("tenantId", tenantId, "userId", userId));
            jdbc.update("""
                    insert into platform.ruoyi_user_binding
                        (ruoyi_user_id, business_tenant_id, status)
                    values (:userId, :tenantId, 'ACTIVE')
                    on conflict (ruoyi_user_id) do update
                        set business_tenant_id = excluded.business_tenant_id,
                            status = excluded.status,
                            updated_at = current_timestamp
                    """, java.util.Map.of("tenantId", tenantId, "userId", userId));
            jdbc.update("""
                    insert into platform.business_membership
                        (tenant_id, ruoyi_user_id, business_role, status)
                    values (:tenantId, :userId, 'OWNER', 'ACTIVE')
                    on conflict (tenant_id, ruoyi_user_id) do update
                        set status = excluded.status
                    """, java.util.Map.of("tenantId", tenantId, "userId", userId));
            jdbc.update("""
                    insert into platform.profile_version
                        (business_tenant_id, ruoyi_user_id, profile_version_id,
                         version_no, content_hash, profile_payload)
                    values (:tenantId, :userId, :profileId, 1, :contentHash,
                            cast(:payload as jsonb))
                    on conflict (business_tenant_id, profile_version_id, version_no)
                    do nothing
                    """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("userId", userId)
                    .addValue("profileId", "ruoyi-profile-" + userId)
                    .addValue("contentHash", "ruoyi-profile-v1-" + userId)
                    .addValue("payload", "{}"));
            return com.ruoyi.interview.domain.platform.TenantId.of(tenantId);
        };
    }

    /**
     * Profile 归属只读取 PostgreSQL 业务表，RuoYi user_id 由 SecurityContext 传入；
     * 不再依赖旧 identity.user_account/membership 表。
     */
    @Bean
    public ProfileAccessPort interviewProfileAccessPort(
            @Qualifier("interviewJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        return (TenantId tenantId, UserId userId, ImmutableVersionRef profileVersion) ->
                !jdbc.query("""
                        select 1
                          from platform.profile_version
                         where business_tenant_id = :tenantId
                           and ruoyi_user_id = :userId
                           and profile_version_id = :profileVersionId
                           and version_no = :versionNo
                           and content_hash = :contentHash
                         limit 1
                        """, java.util.Map.of(
                        "tenantId", tenantId.value(),
                        "userId", Long.parseLong(userId.value()),
                        "profileVersionId", profileVersion.resourceId().value(),
                        "versionNo", profileVersion.versionNo(),
                        "contentHash", profileVersion.contentHash()),
                        (row, rowNum) -> 1).isEmpty();
    }

    @Bean
    public com.ruoyi.interview.controller.rest.common.RequestContextFactory interviewRequestContextFactory(
            RuoYiPrincipalFacade principals, BusinessTenantResolver tenants, ClockPort clock) {
        return new com.ruoyi.interview.controller.rest.common.RequestContextFactory(principals, tenants, clock);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
