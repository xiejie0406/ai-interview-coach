package com.ruoyi.aps.application.foundation;

/**
 * 提供当前写操作的审计操作者。
 */
@FunctionalInterface
public interface ApsAuditActorProvider
{
    ApsAuditActor currentActor();
}
