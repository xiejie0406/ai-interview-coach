package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.inbound.rest.common.RequestContextFactory;
import com.aiinterviewcoach.adapters.inbound.rest.identity.SessionCookiePolicy;
import com.aiinterviewcoach.adapters.outbound.governance.ConfiguredRegistrationPolicyAdapter;
import com.aiinterviewcoach.adapters.outbound.persistence.identity.JdbcRegistrationIdempotencyRepository;
import com.aiinterviewcoach.adapters.outbound.persistence.governance.JdbcConsentPolicyRegistryRepository;
import com.aiinterviewcoach.application.governance.internal.DefaultRegistrationConsentPort;
import com.aiinterviewcoach.application.governance.port.ConsentPolicyRegistryPort;
import com.aiinterviewcoach.application.governance.port.ConsentRepository;
import com.aiinterviewcoach.application.identity.AuthenticateUser;
import com.aiinterviewcoach.application.identity.GetCurrentAccount;
import com.aiinterviewcoach.application.identity.Logout;
import com.aiinterviewcoach.application.identity.RegisterUser;
import com.aiinterviewcoach.application.identity.ResolvePrincipal;
import com.aiinterviewcoach.application.identity.UpdateProfile;
import com.aiinterviewcoach.application.identity.internal.ActivePrincipalGuard;
import com.aiinterviewcoach.application.identity.internal.DefaultAuthenticateUser;
import com.aiinterviewcoach.application.identity.internal.DefaultGetCurrentAccount;
import com.aiinterviewcoach.application.identity.internal.DefaultLogout;
import com.aiinterviewcoach.application.identity.internal.DefaultRegisterUser;
import com.aiinterviewcoach.application.identity.internal.DefaultResolvePrincipal;
import com.aiinterviewcoach.application.identity.internal.DefaultUpdateProfile;
import com.aiinterviewcoach.application.identity.internal.RegistrationIdentifierPort;
import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.identity.port.RegistrationConsentPort;
import com.aiinterviewcoach.application.identity.port.RegistrationIdempotencyPort;
import com.aiinterviewcoach.application.identity.port.RegistrationPolicyPort;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.boot.properties.RegistrationPolicyProperties;
import com.aiinterviewcoach.boot.properties.RuntimeSecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

/** Composition root for the independently gated identity vertical slice. */
@Configuration
public class IdentityUseCaseConfiguration {

    @Bean
    @ConditionalOnMissingBean(RegistrationPolicyPort.class)
    RegistrationPolicyPort registrationPolicy(RegistrationPolicyProperties properties) {
        return new ConfiguredRegistrationPolicyAdapter(
                properties.getSupportedLocales(),
                Map.of(
                        com.aiinterviewcoach.domain.governance.ConsentPurpose.SERVICE_TERMS,
                        new com.aiinterviewcoach.domain.platform.ImmutableVersionRef(
                                com.aiinterviewcoach.domain.platform.ResourceId.of(properties.getServiceTerms().getId()),
                                properties.getServiceTerms().getVersionNo(), properties.getServiceTerms().getContentHash()),
                        com.aiinterviewcoach.domain.governance.ConsentPurpose.PRIVACY_NOTICE,
                        new com.aiinterviewcoach.domain.platform.ImmutableVersionRef(
                                com.aiinterviewcoach.domain.platform.ResourceId.of(properties.getPrivacyNotice().getId()),
                                properties.getPrivacyNotice().getVersionNo(), properties.getPrivacyNotice().getContentHash())));
    }

    @Bean
    @ConditionalOnMissingBean(RegistrationIdempotencyPort.class)
    RegistrationIdempotencyPort registrationIdempotency(NamedParameterJdbcTemplate jdbc,
                                                        com.aiinterviewcoach.adapters.outbound.persistence.shared.PersistenceJsonCodec json,
                                                        RuntimeSecurityProperties properties) {
        return new JdbcRegistrationIdempotencyRepository(jdbc, json, properties.getIdempotencyTtl());
    }

    @Bean
    @ConditionalOnMissingBean(RegistrationConsentPort.class)
    RegistrationConsentPort registrationConsent(ConsentRepository repository, IdGeneratorPort ids,
                                                ConsentPolicyRegistryPort policies) {
        return new DefaultRegistrationConsentPort(repository, ids, policies);
    }

    @Bean
    @ConditionalOnMissingBean(ConsentPolicyRegistryPort.class)
    ConsentPolicyRegistryPort consentPolicyRegistry(NamedParameterJdbcTemplate jdbc) {
        return new JdbcConsentPolicyRegistryRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(ActivePrincipalGuard.class)
    ActivePrincipalGuard activePrincipalGuard(IdentityRepository repository) {
        return new ActivePrincipalGuard(repository);
    }

    @Bean
    @ConditionalOnMissingBean(ResolvePrincipal.class)
    ResolvePrincipal resolvePrincipal(WebSessionPort sessions, ActivePrincipalGuard principal,
                                     IdentityRepository repository) {
        return new DefaultResolvePrincipal(sessions, principal, repository);
    }

    @Bean
    @ConditionalOnMissingBean(RequestContextFactory.class)
    RequestContextFactory requestContextFactory(ResolvePrincipal principals, ClockPort clock) {
        return new RequestContextFactory(principals, clock);
    }

    @Bean
    @ConditionalOnMissingBean(RegisterUser.class)
    RegisterUser registerUser(IdentityChannelPort channel, RegistrationPolicyPort policies,
                              IdentityRepository repository, RegistrationIdentifierPort identifiers,
                              RegistrationConsentPort consents, IdGeneratorPort ids,
                              RegistrationIdempotencyPort idempotency, DomainEventPort events,
                              TransactionPort transaction) {
        return new DefaultRegisterUser(channel, policies, repository, identifiers, consents, ids,
                idempotency, events, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticateUser.class)
    AuthenticateUser authenticateUser(IdentityChannelPort channel, IdentityRepository repository,
                                      WebSessionPort sessions) {
        return new DefaultAuthenticateUser(channel, repository, sessions);
    }

    @Bean
    @ConditionalOnMissingBean(GetCurrentAccount.class)
    GetCurrentAccount currentAccount(IdentityRepository repository, ActivePrincipalGuard principal) {
        return new DefaultGetCurrentAccount(repository, principal);
    }

    @Bean
    @ConditionalOnMissingBean(Logout.class)
    Logout logout(ActivePrincipalGuard principal, WebSessionPort sessions, IdempotencyGuard idempotency,
                 TransactionPort transaction) {
        return new DefaultLogout(principal, sessions, idempotency, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(UpdateProfile.class)
    UpdateProfile updateProfile(IdentityRepository repository, ActivePrincipalGuard principal,
                                IdGeneratorPort ids, IdempotencyGuard idempotency, DomainEventPort events,
                                TransactionPort transaction) {
        return new DefaultUpdateProfile(repository, principal, ids, idempotency, events, transaction);
    }

    @Bean
    SessionCookiePolicy sessionCookiePolicy() {
        return new SessionCookiePolicy(true, "Strict", "/");
    }
}
