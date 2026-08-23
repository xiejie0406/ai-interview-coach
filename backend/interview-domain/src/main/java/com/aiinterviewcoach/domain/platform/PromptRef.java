package com.aiinterviewcoach.domain.platform;

/** Prompt 的不可变版本引用。 */
public record PromptRef(ImmutableVersionRef value) {

    public PromptRef {
        DomainPreconditions.requireNonNull(value, "promptVersionRef");
    }
}
