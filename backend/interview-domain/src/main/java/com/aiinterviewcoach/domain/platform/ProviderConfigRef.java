package com.aiinterviewcoach.domain.platform;

/** 不含 Secret 的 Provider 配置版本引用。 */
public record ProviderConfigRef(String capability, String providerAlias, String modelAlias, int configVersion) {

    public ProviderConfigRef {
        capability = DomainPreconditions.requireText(capability, "capability");
        providerAlias = DomainPreconditions.requireText(providerAlias, "providerAlias");
        modelAlias = DomainPreconditions.requireText(modelAlias, "modelAlias");
        DomainPreconditions.require(configVersion > 0, DomainErrorCode.INVALID_ARGUMENT,
                "provider config version must be positive");
    }
}
