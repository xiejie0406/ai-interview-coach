package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.PromptRef;
import com.aiinterviewcoach.domain.platform.PromptSchemaPin;
import com.aiinterviewcoach.domain.platform.SchemaRef;

/** 将业务固定的 key/version 解析成内容寻址 Prompt/Schema 引用；缺注册项必须 fail-closed。 */
public interface PromptSchemaRegistryPort {

    Resolved resolve(PromptSchemaPin pin);

    record Resolved(PromptRef promptRef, SchemaRef schemaRef) {
        public Resolved {
            DomainPreconditions.requireNonNull(promptRef, "promptRef");
            DomainPreconditions.requireNonNull(schemaRef, "schemaRef");
        }
    }
}
