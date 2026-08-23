package com.ruoyi.interview.infrastructure.provider;

import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ProviderFailure;
import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.domain.platform.RetryDisposition;

import java.util.Optional;

/** 安全默认 ASR：不读取音频、不创建网络客户端，只返回稳定不可用结果。 */
public final class DisabledSpeechToTextAdapter implements SpeechToTextPort, ProviderAdapter {
    public static final String ADAPTER_ID = "speech-to-text-disabled";
    public static final String REASON_CODE = "ASR_NOT_CONFIGURED";

    @Override
    public Result transcribe(Request request, InvocationContext context) {
        return new Failure(new ProviderFailure(REASON_CODE, RetryDisposition.NOT_RETRYABLE,
                Optional.empty(), Optional.empty()));
    }

    @Override public String adapterId() { return ADAPTER_ID; }
    @Override public boolean available() { return false; }
    @Override public String reasonCode() { return REASON_CODE; }
}

