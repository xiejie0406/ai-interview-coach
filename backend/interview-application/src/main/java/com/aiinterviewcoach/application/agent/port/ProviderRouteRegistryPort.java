package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.application.agent.ProviderRoutePlan;
import com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot;

/** 以固定 routePlanId 解析批准的 ProviderConfigRef 主备顺序；不包含 Secret。 */
public interface ProviderRouteRegistryPort {

    ProviderRoutePlan resolve(String capability, ProviderPolicySnapshot policySnapshot);
}
