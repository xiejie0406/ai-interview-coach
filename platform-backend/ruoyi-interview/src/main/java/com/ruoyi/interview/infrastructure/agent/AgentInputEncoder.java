package com.ruoyi.interview.infrastructure.agent;

import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/** 只在受控调用内存中生成 Provider 输入；编码失败不回退到 toString。 */
final class AgentInputEncoder {

    private final ObjectMapper objectMapper;

    AgentInputEncoder(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    String encode(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(Map.copyOf(payload));
        } catch (JacksonException exception) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "agent input could not be encoded", false,
                    Map.of("reasonCode", "AGENT_INPUT_ENCODING_FAILED"));
        }
    }
}

