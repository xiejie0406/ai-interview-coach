package com.ruoyi.interview.domain.platform;

/**
 * 已认证主体的最小领域引用。角色和当前授权由应用层在每个命令处解析，不能由客户端传入。
 */
public record PrincipalRef(TenantId tenantId, UserId userId) {

    public PrincipalRef {
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(userId, "userId");
    }
}
