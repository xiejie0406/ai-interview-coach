package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.infrastructure.OutboundAdapterAvailability;

/** Provider 集成的 adapter 侧标记；application 代码只依赖具体能力 port。 */
public interface ProviderAdapter extends OutboundAdapterAvailability {
    default java.util.Map<String, String> lastObservation() { return java.util.Map.of("status", "NOT_CHECKED"); }
}


