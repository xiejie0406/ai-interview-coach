package com.ruoyi.interview.domain.platform;

import java.util.UUID;

/** 强类型、不透明用户标识；底层 UUID/ULID 选择保持在待决实现边界。 */
public record UserId(String value) {

    public UserId {
        value = DomainPreconditions.requireText(value, "userId");
        DomainPreconditions.require(value.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "userId exceeds maximum length");
        DomainPreconditions.require(value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
                DomainErrorCode.INVALID_ARGUMENT, "userId contains unsupported characters");
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    public static UserId of(UUID value) {
        return new UserId(DomainPreconditions.requireNonNull(value, "userId").toString());
    }
}
