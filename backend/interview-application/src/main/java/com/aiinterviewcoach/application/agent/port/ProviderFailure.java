package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.RetryDisposition;

import java.time.Duration;
import java.util.Optional;

/** 供应商错误的稳定分类；不向核心或前端泄漏 SDK 异常/响应正文。 */
public record ProviderFailure(
        String errorClass,
        RetryDisposition retryDisposition,
        Optional<Duration> retryAfter,
        Optional<String> providerRequestIdHash
) {

    public ProviderFailure {
        errorClass = DomainPreconditions.requireText(errorClass, "providerErrorClass");
        DomainPreconditions.requireNonNull(retryDisposition, "retryDisposition");
        retryAfter = retryAfter == null ? Optional.empty() : retryAfter;
        providerRequestIdHash = providerRequestIdHash == null ? Optional.empty() : providerRequestIdHash;
        providerRequestIdHash.ifPresent(value -> DomainPreconditions.requireText(value, "providerRequestIdHash"));
    }
}
