package com.ruoyi.interview.domain.platform;

/** 已认证内部服务主体；actorId 是批准的稳定别名，不是 Secret、token 或主机名。 */
public record ServiceActorRef(TenantId tenantId, String actorId) {

    public ServiceActorRef {
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        actorId = DomainPreconditions.requireText(actorId, "serviceActorId");
        DomainPreconditions.require(actorId.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "service actor id exceeds maximum length");
        DomainPreconditions.require(actorId.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
                DomainErrorCode.INVALID_ARGUMENT, "service actor id contains unsupported characters");
    }
}
