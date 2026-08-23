package com.aiinterviewcoach.domain.platform;

/** 客户端操作键；业务 ID、资源 ID 与此键保持不同语义。 */
public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 256;

    public IdempotencyKey {
        value = DomainPreconditions.requireText(value, "idempotencyKey");
        DomainPreconditions.require(value.length() <= MAX_LENGTH, DomainErrorCode.INVALID_ARGUMENT,
                "idempotencyKey exceeds maximum length");
        DomainPreconditions.require(value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
                DomainErrorCode.INVALID_ARGUMENT, "idempotencyKey contains unsupported characters");
    }
}
