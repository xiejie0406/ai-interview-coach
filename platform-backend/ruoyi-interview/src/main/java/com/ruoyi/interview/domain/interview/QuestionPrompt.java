package com.ruoyi.interview.domain.interview;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.PromptRef;
import com.ruoyi.interview.domain.platform.SchemaRef;

import java.util.Optional;

/** 候选问题通过应用层门禁后才能提交为 Turn 事实。 */
public record QuestionPrompt(
        String text,
        String contentHash,
        Optional<PromptRef> promptRef,
        Optional<SchemaRef> schemaRef
) {

    public QuestionPrompt {
        text = DomainPreconditions.requireText(text, "questionText");
        contentHash = DomainPreconditions.requireText(contentHash, "questionContentHash");
        promptRef = promptRef == null ? Optional.empty() : promptRef;
        schemaRef = schemaRef == null ? Optional.empty() : schemaRef;
    }

    @Override
    public String toString() {
        return "QuestionPrompt[text=<redacted>, contentHash=" + contentHash
                + ", promptRef=" + promptRef + ", schemaRef=" + schemaRef + "]";
    }
}
