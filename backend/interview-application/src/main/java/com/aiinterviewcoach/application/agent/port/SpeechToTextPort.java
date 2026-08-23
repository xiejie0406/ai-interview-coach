package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.ArtifactRef;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ProviderConfigRef;
import com.aiinterviewcoach.domain.voice.ConfidenceSpan;

import java.util.List;
import java.util.Optional;

/** Provider-neutral ASR 端口；adapter 必须返回稳定失败，不能抛出供应商 SDK 类型。 */
@FunctionalInterface
public interface SpeechToTextPort {

    Result transcribe(Request request, InvocationContext context);

    record Request(
            ArtifactRef audioArtifact,
            String language,
            List<String> hotwords,
            String offsetUnit,
            ProviderConfigRef providerConfigRef
    ) {
        public Request {
            DomainPreconditions.requireNonNull(audioArtifact, "audioArtifact");
            language = DomainPreconditions.requireText(language, "asrLanguage");
            hotwords = List.copyOf(hotwords == null ? List.of() : hotwords);
            hotwords.forEach(value -> DomainPreconditions.requireText(value, "asrHotword"));
            offsetUnit = DomainPreconditions.requireText(offsetUnit, "asrOffsetUnit");
            DomainPreconditions.require(offsetUnit.equals("UTF16")
                            || offsetUnit.equals("UNICODE_CODE_POINT"),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "unsupported ASR offset unit");
            DomainPreconditions.requireNonNull(providerConfigRef, "providerConfigRef");
        }

        @Override
        public String toString() {
            return "Request[audioArtifact=" + audioArtifact.artifactId() + ", language=" + language
                    + ", hotwords=<redacted:" + hotwords.size() + ">, offsetUnit=" + offsetUnit
                    + ", providerConfigRef=" + providerConfigRef + "]";
        }
    }

    sealed interface Result permits Success, Failure {
    }

    record Success(
            String transcriptText,
            String language,
            String offsetUnit,
            List<ConfidenceSpan> lowConfidenceSpans,
            ModelUsage usage,
            Optional<String> providerRequestIdHash
    ) implements Result {
        public Success {
            transcriptText = DomainPreconditions.requireText(transcriptText, "transcriptText");
            language = DomainPreconditions.requireText(language, "transcriptLanguage");
            offsetUnit = DomainPreconditions.requireText(offsetUnit, "transcriptOffsetUnit");
            DomainPreconditions.require(offsetUnit.equals("UTF16")
                            || offsetUnit.equals("UNICODE_CODE_POINT"),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "unsupported transcript offset unit");
            lowConfidenceSpans = List.copyOf(lowConfidenceSpans == null ? List.of() : lowConfidenceSpans);
            DomainPreconditions.requireNonNull(usage, "modelUsage");
            providerRequestIdHash = providerRequestIdHash == null ? Optional.empty() : providerRequestIdHash;
        }

        @Override
        public String toString() {
            return "Success[transcriptText=<redacted>, language=" + language + ", offsetUnit="
                    + offsetUnit + ", lowConfidenceSpans=" + lowConfidenceSpans.size() + ", usage="
                    + usage + ", providerRequestIdHash=" + providerRequestIdHash + "]";
        }
    }

    record Failure(ProviderFailure failure) implements Result {
        public Failure {
            DomainPreconditions.requireNonNull(failure, "providerFailure");
        }
    }
}
