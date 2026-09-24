package com.ruoyi.aden.domain.runner;

public record AdenCredentialId(String value) {
    public AdenCredentialId { value = AdenRunnerIds.requireUuid(value, "credentialId"); }
}
