package com.aiinterviewcoach.domain.platform;

/** 用于串联命令、事件、Job 和审计的非敏感关联标识。 */
public record CorrelationId(String value) {

    private static final int MAX_LENGTH = 128;

    public CorrelationId {
        value = DomainPreconditions.requireText(value, "correlationId");
        DomainPreconditions.require(value.length() <= MAX_LENGTH, DomainErrorCode.INVALID_ARGUMENT,
                "correlationId exceeds maximum length");
        DomainPreconditions.require(value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
                DomainErrorCode.INVALID_ARGUMENT, "correlationId contains unsupported characters");
    }
}
