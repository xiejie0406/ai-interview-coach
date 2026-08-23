package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ProviderConfigRef;

import java.util.Optional;

/** Provider-neutral TTS 端口；音频正文通过受控 sink 写入，不作为返回 DTO 常驻。 */
@FunctionalInterface
public interface TextToSpeechPort {

    Result synthesize(Request request, AudioSink sink, InvocationContext context);

    record Request(
            String text,
            String language,
            String voiceProfile,
            String codec,
            ProviderConfigRef providerConfigRef
    ) {
        public Request {
            text = DomainPreconditions.requireText(text, "ttsText");
            language = DomainPreconditions.requireText(language, "ttsLanguage");
            voiceProfile = DomainPreconditions.requireText(voiceProfile, "ttsVoiceProfile");
            codec = DomainPreconditions.requireText(codec, "ttsCodec");
            DomainPreconditions.requireNonNull(providerConfigRef, "providerConfigRef");
        }

        @Override
        public String toString() {
            return "Request[text=<redacted>, language=" + language + ", voiceProfile="
                    + voiceProfile + ", codec=" + codec + ", providerConfigRef="
                    + providerConfigRef + "]";
        }
    }

    interface AudioSink {
        void accept(long sequence, byte[] bytes, boolean endOfOutput);
    }

    sealed interface Result permits Success, Failure {
    }

    record Success(long bytes, long durationMillis, ModelUsage usage,
                   Optional<String> providerRequestIdHash) implements Result {
        public Success {
            DomainPreconditions.require(bytes > 0 && durationMillis > 0,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "TTS size and duration must be positive");
            DomainPreconditions.requireNonNull(usage, "modelUsage");
            providerRequestIdHash = providerRequestIdHash == null ? Optional.empty() : providerRequestIdHash;
        }
    }

    record Failure(ProviderFailure failure) implements Result {
        public Failure {
            DomainPreconditions.requireNonNull(failure, "providerFailure");
        }
    }
}
