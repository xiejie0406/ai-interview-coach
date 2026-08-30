package com.ruoyi.interview.domain.platform;

import java.util.Map;
import java.util.TreeMap;

/** Provider 主备路由与模型策略快照；不包含 API key、Secret、Prompt 或供应商响应。 */
public record ProviderPolicySnapshot(
        String routePlanId,
        String primaryProvider,
        String primaryModel,
        String fallbackProvider,
        String fallbackModel,
        Map<String, String> safetyFlags
) {
    public ProviderPolicySnapshot {
        routePlanId = DomainPreconditions.requireText(routePlanId, "routePlanId");
        primaryProvider = DomainPreconditions.requireText(primaryProvider, "primaryProvider");
        primaryModel = DomainPreconditions.requireText(primaryModel, "primaryModel");
        DomainPreconditions.require(routePlanId.length() <= 128 && primaryProvider.length() <= 128
                        && primaryModel.length() <= 160,
                DomainErrorCode.INVALID_ARGUMENT, "provider route field is too long");
        DomainPreconditions.require((fallbackProvider == null) == (fallbackModel == null),
                DomainErrorCode.INVALID_ARGUMENT,
                "fallback provider and model must be configured together");
        if (fallbackProvider != null) {
            fallbackProvider = DomainPreconditions.requireText(fallbackProvider, "fallbackProvider");
            fallbackModel = DomainPreconditions.requireText(fallbackModel, "fallbackModel");
            DomainPreconditions.require(fallbackProvider.length() <= 128 && fallbackModel.length() <= 160,
                    DomainErrorCode.INVALID_ARGUMENT, "fallback provider route field is too long");
        }
        safetyFlags = Map.copyOf(new TreeMap<>(safetyFlags == null ? Map.of() : safetyFlags));
        safetyFlags.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "safetyFlagKey");
            DomainPreconditions.requireText(value, "safetyFlagValue");
        });
    }

    @Override
    public String toString() {
        return "ProviderPolicySnapshot[routePlanId=" + routePlanId
                + ", primaryProvider=" + primaryProvider + ", primaryModel=" + primaryModel
                + ", fallbackProvider=" + (fallbackProvider == null ? "<absent>" : "<present>")
                + ", fallbackModel=" + (fallbackModel == null ? "<absent>" : "<present>")
                + ", safetyFlags=<redacted>]";
    }
}
