package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.TenantId;

/** 将 owner-scoped 短期 content ref 解析为调用内存正文；实现不得缓存、记录或跨 tenant 解析。 */
public interface AgentContentResolverPort {

    String resolve(TenantId tenantId, String contentReference, String purpose);
}
