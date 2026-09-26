package com.ruoyi.interview.application.agent;

import com.ruoyi.interview.application.agent.port.ProviderFailure;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ProviderConfigRef;
import com.ruoyi.interview.domain.platform.RetryDisposition;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 已批准 route version 的主备顺序。只对可自动重试的瞬态失败切换，且不循环、不越过切换上限。
 */
public record ProviderRoutePlan(
        ImmutableVersionRef routeVersion,
        ProviderConfigRef primary,
        List<ProviderConfigRef> fallbacks,
        int maxSwitches
) {

    public ProviderRoutePlan {
        DomainPreconditions.requireNonNull(routeVersion, "providerRouteVersion");
        DomainPreconditions.requireNonNull(primary, "primaryProviderConfig");
        fallbacks = List.copyOf(fallbacks == null ? List.of() : fallbacks);
        DomainPreconditions.require(maxSwitches >= 0 && maxSwitches <= fallbacks.size(),
                DomainErrorCode.INVALID_ARGUMENT, "provider max switches is outside fallback range");
        Set<ProviderConfigRef> unique = new HashSet<>(fallbacks);
        DomainPreconditions.require(unique.size() == fallbacks.size() && !unique.contains(primary),
                DomainErrorCode.INVALID_ARGUMENT, "provider route must not contain duplicate configs");
        fallbacks.forEach(config -> DomainPreconditions.require(
                config.capability().equals(primary.capability()), DomainErrorCode.INVALID_ARGUMENT,
                "provider route capabilities must match"));
    }

    public Optional<ProviderConfigRef> next(
            ProviderConfigRef current,
            ProviderFailure failure,
            Set<ProviderConfigRef> attempted
    ) {
        DomainPreconditions.requireNonNull(current, "currentProviderConfig");
        DomainPreconditions.requireNonNull(failure, "providerFailure");
        Set<ProviderConfigRef> attemptedConfigs = Set.copyOf(attempted == null ? Set.of() : attempted);
        long switchesAlreadyUsed = attemptedConfigs.stream().filter(config -> !config.equals(primary)).count();
        if (failure.retryDisposition() != RetryDisposition.SAFE_BACKOFF
                || switchesAlreadyUsed >= maxSwitches) {
            return Optional.empty();
        }
        return fallbacks.stream()
                .filter(candidate -> !candidate.equals(current) && !attemptedConfigs.contains(candidate))
                .findFirst();
    }
}
