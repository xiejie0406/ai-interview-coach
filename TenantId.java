package com.aiinterviewcoach.domain.platform;

import java.util.UUID;

/** 强类型、不透明租户标识；底层 UUID/ULID 选择保持在待决实现边界。 */
public record TenantId(String value) {

    public TenantId {
        value = DomainPreconditions.requireText(value, "tenantId");
        DomainPreconditions.require(value.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "tenantId exceeds maximum length");
        DomainPreconditions.require(value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
                DomainErrorCode.INVALID_ARGUMENT, "tenantId contains unsupported characters");
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    public static TenantId of(UUID value) {
        return new TenantId(DomainPreconditions.requireNonNull(value, "tenantId").toString());
    }
}
