package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.application.agent.port.ChatModelRequest;
import com.ruoyi.interview.application.agent.port.ChatModelResult;
import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ProviderFailure;
import com.ruoyi.interview.domain.platform.RetryDisposition;

import java.util.Optional;

/** 安全默认实现：不进行网络调用，只返回稳定的 capability unavailable 结果。 */
public final class DisabledChatModelAdapter implements ChatModelPort, ProviderAdapter {
    public static final String ADAPTER_ID = "chat-model-disabled";
    public static final String REASON_CODE = "CAPABILITY_NOT_CONFIGURED";

    @Override
    public ChatModelResult execute(ChatModelRequest request, InvocationContext context) {
        return new ChatModelResult.Failure(new ProviderFailure(
                REASON_CODE,
                RetryDisposition.NOT_RETRYABLE,
                Optional.empty(),
                Optional.empty()));
    }

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String reasonCode() {
        return REASON_CODE;
    }
}

