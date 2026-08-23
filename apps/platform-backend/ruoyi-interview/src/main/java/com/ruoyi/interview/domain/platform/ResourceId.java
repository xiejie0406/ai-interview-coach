package com.ruoyi.interview.domain.platform;

import java.util.UUID;

/**
 * 跨域稳定、不透明资源引用。底层 UUID/ULID 待决；任何格式都不能当作跨租户访问许可。
 */
public record ResourceId(String value) {

    public ResourceId {
        value = DomainPreconditions.requireText(value, "resourceId");
        DomainPreconditions.require(value.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "resourceId exceeds maximum length");
        DomainPreconditions.require(value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
                DomainErrorCode.INVALID_ARGUMENT, "resourceId contains unsupported characters");
    }

    public static ResourceId of(String value) {
        return new ResourceId(value);
    }

    public static ResourceId of(UUID value) {
        return new ResourceId(DomainPreconditions.requireNonNull(value, "resourceId").toString());
    }
}
