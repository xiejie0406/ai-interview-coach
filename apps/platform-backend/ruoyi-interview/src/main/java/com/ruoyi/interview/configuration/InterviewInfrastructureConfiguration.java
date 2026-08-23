package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.security.BusinessTenantResolver;
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
import com.ruoyi.interview.controller.rest.health.ServiceMetadata;
import com.ruoyi.interview.configuration.properties.ProviderProperties;
import com.ruoyi.interview.configuration.properties.VoiceRuntimeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/** ruoyi-interview 的唯一组合根：业务 PostgreSQL、RuoYi principal、Provider 和本地事务边界。 */
@Configuration
@EnableConfigurationProperties({ProviderProperties.class, VoiceRuntimeProperties.class})
public class InterviewInfrastructureConfiguration {

    @Bean(name = "interviewDataSource")
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "interview.datasource")
    public DataSource interviewDataSource() {
        return org.springframework.boot.jdbc.DataSourceBuilder.create().build();
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
    public Clock interviewClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClockPort interviewClockPort(Clock interviewClock) {
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
        try {
            return new AesGcmSensitiveEnvelopeCipher(keyId, key, new SecureRandom());
        } finally {
            Arrays.fill(key, (byte) 0);
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
    public BusinessTenantResolver interviewBusinessTenantResolver() {
        return userId -> com.ruoyi.interview.domain.platform.TenantId.of("user-" + userId);
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
