package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.outbound.crypto.Sha256ContentDigestAdapter;
import com.aiinterviewcoach.adapters.outbound.provider.DeepSeekChatModelAdapter;
import com.aiinterviewcoach.adapters.outbound.provider.AudioArtifactSource;
import com.aiinterviewcoach.adapters.outbound.provider.DisabledChatModelAdapter;
import com.aiinterviewcoach.adapters.outbound.provider.DisabledSpeechToTextAdapter;
import com.aiinterviewcoach.adapters.outbound.provider.DisabledTextToSpeechAdapter;
import com.aiinterviewcoach.adapters.outbound.provider.VolcengineSpeechToTextAdapter;
import com.aiinterviewcoach.adapters.outbound.provider.VolcengineTextToSpeechAdapter;
import com.aiinterviewcoach.adapters.outbound.storage.ObjectStorageAdapter;
import com.aiinterviewcoach.adapters.outbound.storage.LocalFileObjectStorageAdapter;
import com.aiinterviewcoach.adapters.outbound.storage.UnavailableObjectStorageAdapter;
import com.aiinterviewcoach.application.agent.port.ChatModelPort;
import com.aiinterviewcoach.application.agent.port.SpeechToTextPort;
import com.aiinterviewcoach.application.agent.port.TextToSpeechPort;
import com.aiinterviewcoach.application.integration.port.ObjectStoragePort;
import com.aiinterviewcoach.application.voice.port.ContentDigestPort;
import com.aiinterviewcoach.boot.properties.ProviderProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.URI;
import java.time.Duration;
import java.nio.file.Path;

/** Provider 与 Foundation adapter 的受控装配；真实外部调用仍由 safety gate 控制。 */
@Configuration
public class ProviderConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "interview.foundation-safety", name = "object-storage-writes-enabled",
            havingValue = "true")
    LocalFileObjectStorageAdapter localFileObjectStorageAdapter() {
        return new LocalFileObjectStorageAdapter(Path.of(".runtime", "voice-artifacts"));
    }
    /**
     * 真实 HTTP adapter 仍受 foundation safety gate 控制；Key 缺失时 adapter 自身也只返回稳定失败。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "interview.foundation-safety",
            name = "external-provider-calls-enabled",
            havingValue = "true")
    DeepSeekChatModelAdapter deepSeekChatModelAdapter(
            ObjectMapper objectMapper,
            ProviderProperties providerProperties
    ) {
        ProviderProperties.DeepSeek deepseek = providerProperties.getDeepseek();
        return new DeepSeekChatModelAdapter(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                objectMapper,
                deepseek.getApiKey(),
                deepseek.getBaseUrl(),
                deepseek.getInterviewModel(),
                deepseek.getEvaluationModel(),
                deepseek.getThinkingMode());
    }

    @Bean
    @ConditionalOnBean(AudioArtifactSource.class)
    @ConditionalOnProperty(
            prefix = "interview.foundation-safety",
            name = "external-provider-calls-enabled",
            havingValue = "true")
    VolcengineSpeechToTextAdapter volcengineSpeechToTextAdapter(
            ObjectMapper objectMapper,
            ProviderProperties providerProperties,
            AudioArtifactSource artifactSource
    ) {
        ProviderProperties.VolcengineSpeech speech = providerProperties.getVolcengineSpeech();
        ProviderProperties.Asr asr = speech.getAsr();
        return new VolcengineSpeechToTextAdapter(
                providerHttpClient(), objectMapper, artifactSource, speech.getApiKey(),
                asr.getModel(), URI.create(asr.getEndpoint()));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "interview.foundation-safety",
            name = "external-provider-calls-enabled",
            havingValue = "true")
    VolcengineTextToSpeechAdapter volcengineTextToSpeechAdapter(
            ObjectMapper objectMapper,
            ProviderProperties providerProperties
    ) {
        ProviderProperties.VolcengineSpeech speech = providerProperties.getVolcengineSpeech();
        ProviderProperties.Tts tts = speech.getTts();
        return new VolcengineTextToSpeechAdapter(
                providerHttpClient(), objectMapper, speech.getApiKey(), tts.getModel(),
                tts.getVoice(), URI.create(tts.getEndpoint()), tts.getSampleRate());
    }

    @Bean
    @ConditionalOnMissingBean(ChatModelPort.class)
    DisabledChatModelAdapter disabledChatModelAdapter() {
        return new DisabledChatModelAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(SpeechToTextPort.class)
    DisabledSpeechToTextAdapter disabledSpeechToTextAdapter() {
        return new DisabledSpeechToTextAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(TextToSpeechPort.class)
    DisabledTextToSpeechAdapter disabledTextToSpeechAdapter() {
        return new DisabledTextToSpeechAdapter();
    }

    @Bean
    @ConditionalOnMissingBean({ObjectStorageAdapter.class, ObjectStoragePort.class})
    UnavailableObjectStorageAdapter unavailableObjectStorageAdapter() {
        return new UnavailableObjectStorageAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(ContentDigestPort.class)
    Sha256ContentDigestAdapter sha256ContentDigestAdapter() {
        return new Sha256ContentDigestAdapter();
    }

    private static HttpClient providerHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
}
