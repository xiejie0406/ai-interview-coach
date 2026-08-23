package com.aiinterviewcoach.domain.platform;

import java.util.Objects;

/**
 * 领域规则被拒绝时使用的异常。消息只能说明规则，不得携带回答、音频、凭据或供应商正文。
 */
public final class DomainException extends RuntimeException {

    private final DomainErrorCode code;

    public DomainException(DomainErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public DomainErrorCode code() {
        return code;
    }
}
