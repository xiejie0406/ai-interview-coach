package com.ruoyi.interview.application.agent.port;

import com.ruoyi.interview.application.agent.ProviderRoutePlan;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;

/** 以固定 routePlanId 解析批准的 ProviderConfigRef 主备顺序；不包含 Secret。 */
public interface ProviderRouteRegistryPort {

    ProviderRoutePlan resolve(String capability, ProviderPolicySnapshot policySnapshot);
}
