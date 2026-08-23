package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

/** 服务端按 capability 解析一次调用的时间/成本预算；调用方不能自行放大预算。 */
public interface AgentInvocationPolicyPort {

    InvocationContext authorize(
            String capability,
            TenantId tenantId,
            ResourceId businessOperationId,
            CorrelationId correlationId
    );
}
