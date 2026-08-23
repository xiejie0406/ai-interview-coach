package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** Prompt 与结构化输出 schema 的不可变版本钉住信息。 */
@Deprecated(forRemoval = false)
public record PromptSchemaPin(
        String promptKey,
        int promptVersion,
        String schemaKey,
        int schemaVersion
) {

    public PromptSchemaPin {
        promptKey = DomainPreconditions.requireText(promptKey, "promptKey");
        schemaKey = DomainPreconditions.requireText(schemaKey, "schemaKey");
        DomainPreconditions.require(promptKey.length() <= 160 && schemaKey.length() <= 160,
                DomainErrorCode.INVALID_ARGUMENT, "prompt/schema key is too long");
        DomainPreconditions.require(promptVersion > 0, DomainErrorCode.INVALID_ARGUMENT,
                "promptVersion must be positive");
        DomainPreconditions.require(schemaVersion > 0, DomainErrorCode.INVALID_ARGUMENT,
                "schemaVersion must be positive");
    }
}
