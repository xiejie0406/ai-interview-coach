package com.aiinterviewcoach.domain.platform;

import java.time.Instant;
import java.util.Optional;

/** 失败分类不包含供应商正文或堆栈。 */
public record JobFailure(
        String errorCode,
        RetryDisposition retryDisposition,
        Optional<Instant> nextAttemptAt
) {

    public JobFailure {
        errorCode = DomainPreconditions.requireText(errorCode, "jobErrorCode");
        DomainPreconditions.requireNonNull(retryDisposition, "retryDisposition");
        nextAttemptAt = nextAttemptAt == null ? Optional.empty() : nextAttemptAt;
        DomainPreconditions.require(!retryDisposition.permitsAutomaticRetry() || nextAttemptAt.isPresent(),
                DomainErrorCode.INVALID_ARGUMENT,
                "retryable failure requires a next attempt time");
    }
}
