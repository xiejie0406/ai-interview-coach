package com.aiinterviewcoach.application.shared;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用例编排失败；details 仅允许 ID/状态/版本/原因码，不得放正文、Secret、堆栈或供应商响应。
 */
public final class ApplicationException extends RuntimeException {

    private final ApplicationErrorCode code;
    private final boolean retryable;
    private final Map<String, String> details;

    public ApplicationException(
            ApplicationErrorCode code,
            String message,
            boolean retryable,
            Map<String, String> details
    ) {
        super(DomainPreconditions.requireText(message, "applicationErrorMessage"));
        this.code = DomainPreconditions.requireNonNull(code, "applicationErrorCode");
        this.retryable = retryable;
        this.details = Map.copyOf(new LinkedHashMap<>(details == null ? Map.of() : details));
        this.details.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "application error detail key");
            DomainPreconditions.requireText(value, "application error detail value");
        });
    }

    public ApplicationErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public Map<String, String> details() {
        return details;
    }
}
