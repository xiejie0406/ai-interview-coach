package com.ruoyi.interview.infrastructure.agent.config;

import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import com.ruoyi.interview.application.agent.ProviderRoutePlan;
import com.ruoyi.interview.application.agent.port.ProviderRouteRegistryPort;
import com.ruoyi.interview.domain.platform.ProviderConfigRef;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;

import java.util.List;
import java.util.Map;

/** 将持久化策略快照解析为批准的 ProviderConfigRef；不解析 API key。 */
public final class ConfiguredProviderRouteRegistryAdapter implements ProviderRouteRegistryPort {

    private final Map<String, ProviderRoutePlan> routes;

    public ConfiguredProviderRouteRegistryAdapter(Map<String, ProviderRoutePlan> routes) {
        this.routes = Map.copyOf(routes == null ? Map.of() : routes);
    }

    @Override
    public ProviderRoutePlan resolve(String capability, ProviderPolicySnapshot snapshot) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("provider capability must not be blank");
        }
        java.util.Objects.requireNonNull(snapshot, "providerPolicySnapshot");
        ProviderRoutePlan route = routes.get(snapshot.routePlanId());
        if (route == null) {
            throw new AdapterUnavailableException("provider-route-registration");
        }
        if (!route.routeVersion().resourceId().value().equals(snapshot.routePlanId())) {
            throw new IllegalStateException("provider route id does not match its immutable version reference");
        }
        requireMatches(route.primary(), capability, snapshot.primaryProvider(), snapshot.primaryModel());
        List<ProviderConfigRef> fallbacks = route.fallbacks();
        if (snapshot.fallbackProvider() == null) {
            if (!fallbacks.isEmpty()) {
                throw new IllegalStateException("provider route has fallback not present in policy snapshot");
            }
        } else {
            if (fallbacks.size() != 1) {
                throw new IllegalStateException("provider policy snapshot requires exactly one fallback");
            }
            requireMatches(fallbacks.get(0), capability,
                    snapshot.fallbackProvider(), snapshot.fallbackModel());
        }
        return route;
    }

    private static void requireMatches(
            ProviderConfigRef config,
            String capability,
            String provider,
            String model
    ) {
        if (!config.capability().equals(capability)
                || !config.providerAlias().equals(provider)
                || !config.modelAlias().equals(model)) {
            throw new IllegalStateException("provider route does not match persisted policy snapshot");
        }
    }
}


