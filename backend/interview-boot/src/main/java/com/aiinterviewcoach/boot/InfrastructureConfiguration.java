package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.outbound.crypto.AesGcmSensitiveEnvelopeCipher;
import com.aiinterviewcoach.adapters.outbound.identity.JdbcPasswordIdentityChannelAdapter;
import com.aiinterviewcoach.adapters.outbound.identity.JdbcWebSessionAdapter;
import com.aiinterviewcoach.adapters.outbound.persistence.platform.DomainEventEnvelopePolicy;
import com.aiinterviewcoach.adapters.outbound.persistence.platform.DurableStreamEventPolicy;
import com.aiinterviewcoach.adapters.outbound.persistence.platform.StrictDomainEventEnvelopePolicy;
import com.aiinterviewcoach.adapters.outbound.persistence.platform.StrictDurableStreamEventPolicy;
import com.aiinterviewcoach.adapters.outbound.platform.SystemClockAdapter;
import com.aiinterviewcoach.adapters.outbound.platform.UuidIdGeneratorAdapter;
import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.SensitiveEnvelopeCipher;
import com.aiinterviewcoach.boot.properties.RuntimeSecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Composition-root bindings for deterministic platform/security adapters.
 *
 * <p>No business endpoint or background worker is enabled here. Missing or malformed secret material fails bean
 * creation instead of producing a plaintext, fixed-key or fake-provider fallback.</p>
 */
@Configuration
public class InfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ClockPort.class)
    ClockPort clockPort(Clock clock) {
        return new SystemClockAdapter(clock);
    }

    @Bean
    @ConditionalOnMissingBean(SecureRandom.class)
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    @ConditionalOnMissingBean(IdGeneratorPort.class)
    IdGeneratorPort idGeneratorPort() {
        return new UuidIdGeneratorAdapter(UUID::randomUUID);
    }

    @Bean
    @ConditionalOnMissingBean(SensitiveEnvelopeCipher.class)
    SensitiveEnvelopeCipher sensitiveEnvelopeCipher(
            RuntimeSecurityProperties properties,
            SecureRandom secureRandom
    ) {
        return new AesGcmSensitiveEnvelopeCipher(
                properties.getPersistenceKeyId(),
                AesGcmSensitiveEnvelopeCipher.decodeBase64Key(
                        properties.getPersistenceKeyBase64(), "persistenceKeyBase64"),
                secureRandom);
    }

    @Bean
    @ConditionalOnMissingBean(DomainEventEnvelopePolicy.class)
    DomainEventEnvelopePolicy domainEventEnvelopePolicy() {
        return new StrictDomainEventEnvelopePolicy();
    }

    @Bean
    @ConditionalOnMissingBean(DurableStreamEventPolicy.class)
    DurableStreamEventPolicy durableStreamEventPolicy(RuntimeSecurityProperties properties) {
        return new StrictDurableStreamEventPolicy(
                properties.getInterviewStreamRetention(),
                properties.getEvaluationStreamRetention());
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(IdentityChannelPort.class)
    IdentityChannelPort identityChannelPort(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            RuntimeSecurityProperties properties
    ) {
        return new JdbcPasswordIdentityChannelAdapter(
                jdbc,
                passwordEncoder,
                AesGcmSensitiveEnvelopeCipher.decodeBase64Key(
                        properties.getIdentityIdentifierHmacKeyBase64(), "identityIdentifierHmacKeyBase64"));
    }

    @Bean
    @ConditionalOnMissingBean(WebSessionPort.class)
    WebSessionPort webSessionPort(
            NamedParameterJdbcTemplate jdbc,
            RuntimeSecurityProperties properties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        return new JdbcWebSessionAdapter(
                jdbc,
                AesGcmSensitiveEnvelopeCipher.decodeBase64Key(
                        properties.getWebSessionTokenHmacKeyBase64(), "webSessionTokenHmacKeyBase64"),
                secureRandom,
                clock,
                properties.getSessionAbsoluteTtl(),
                properties.getSessionIdleTtl());
    }
}
