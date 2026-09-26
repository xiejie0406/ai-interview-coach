package com.ruoyi.aps.application.foundation;

import java.util.Objects;

/**
 * 写入 APS 审计列的稳定操作者标识；不承载 RuoYi 用户实体。
 */
public record ApsAuditActor(String userId, String username)
{
    public ApsAuditActor
    {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        if (userId.isBlank() || username.isBlank())
        {
            throw new IllegalArgumentException("审计操作者标识不能为空");
        }
    }

    public static ApsAuditActor system(String component)
    {
        return new ApsAuditActor("system:" + component, component);
    }
}
