package com.ruoyi.interview.infrastructure.governance;

import com.ruoyi.interview.application.governance.port.RegistrationPolicyPort;
import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** 由 Boot 已校验配置构造；不硬编码真实政策版本或正文。 */
public final class ConfiguredRegistrationPolicyAdapter implements RegistrationPolicyPort {

    private final Set<String> supportedLocales;
    private final Map<ConsentPurpose, ImmutableVersionRef> requiredPolicies;

    public ConfiguredRegistrationPolicyAdapter(
            Set<String> supportedLocales,
            Map<ConsentPurpose, ImmutableVersionRef> requiredPolicies
    ) {
        this.supportedLocales = Set.copyOf(supportedLocales == null ? Set.of() : supportedLocales);
        this.requiredPolicies = Map.copyOf(requiredPolicies == null ? Map.of() : requiredPolicies);
        if (this.supportedLocales.isEmpty() || !this.requiredPolicies.keySet().equals(Set.of(
                ConsentPurpose.SERVICE_TERMS, ConsentPurpose.PRIVACY_NOTICE))) {
            throw new IllegalArgumentException("registration policy configuration is incomplete");
        }
    }

    @Override
    public Map<ConsentPurpose, ImmutableVersionRef> requiredPolicies(String locale, Instant at) {
        if (locale == null || !supportedLocales.contains(locale)) {
            throw new IllegalArgumentException("registration locale is not supported");
        }
        java.util.Objects.requireNonNull(at, "policyObservedAt");
        return requiredPolicies;
    }
}


