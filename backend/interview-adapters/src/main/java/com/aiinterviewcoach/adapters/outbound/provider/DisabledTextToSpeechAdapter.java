package com.aiinterviewcoach.adapters.outbound.provider;

import com.aiinterviewcoach.application.agent.port.InvocationContext;
import com.aiinterviewcoach.application.agent.port.ProviderFailure;
import com.aiinterviewcoach.application.agent.port.TextToSpeechPort;
import com.aiinterviewcoach.domain.platform.RetryDisposition;

import java.util.Optional;

/** 安全默认 TTS：不读取 sink、不创建音频或网络调用。 */
public final class DisabledTextToSpeechAdapter implements TextToSpeechPort, ProviderAdapter {
    public static final String ADAPTER_ID = "text-to-speech-disabled";
    public static final String REASON_CODE = "TTS_NOT_CONFIGURED";

    @Override
    public Result synthesize(Request request, AudioSink sink, InvocationContext context) {
        return new Failure(new ProviderFailure(REASON_CODE, RetryDisposition.NOT_RETRYABLE,
                Optional.empty(), Optional.empty()));
    }

    @Override public String adapterId() { return ADAPTER_ID; }
    @Override public boolean available() { return false; }
    @Override public String reasonCode() { return REASON_CODE; }
}
