package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** Provider-neutral 的最小消息；role 必须来自 approved allowlist。 */
public record ModelMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    public ModelMessage {
        DomainPreconditions.requireNonNull(role, "modelMessageRole");
        content = DomainPreconditions.requireText(content, "modelMessageContent");
    }

    @Override
    public String toString() {
        return "ModelMessage[role=" + role + ", content=<redacted>]";
    }
}
