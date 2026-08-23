package com.ruoyi.interview.domain.platform;

/** Agent 调用固定的 Prompt 与结构化输出 Schema 版本；不包含 Prompt 正文。 */
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
