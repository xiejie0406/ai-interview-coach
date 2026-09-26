package com.ruoyi.interview.configuration;

import com.ruoyi.interview.infrastructure.crypto.Sha256ContentDigestAdapter;
import com.ruoyi.interview.infrastructure.provider.DeepSeekChatModelAdapter;
import com.ruoyi.interview.infrastructure.provider.ManagedInterviewProviderAdapters;
import com.ruoyi.interview.infrastructure.provider.AudioArtifactSource;
import com.ruoyi.interview.infrastructure.provider.DisabledChatModelAdapter;
import com.ruoyi.interview.infrastructure.provider.DisabledSpeechToTextAdapter;
import com.ruoyi.interview.infrastructure.provider.DisabledTextToSpeechAdapter;
import com.ruoyi.interview.infrastructure.provider.VolcengineSpeechToTextAdapter;
import com.ruoyi.interview.infrastructure.provider.VolcengineTextToSpeechAdapter;
import com.ruoyi.interview.infrastructure.storage.ObjectStorageAdapter;
import com.ruoyi.interview.infrastructure.storage.LocalFileObjectStorageAdapter;
import com.ruoyi.interview.infrastructure.storage.UnavailableObjectStorageAdapter;
import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.application.agent.port.TextToSpeechPort;
import com.ruoyi.interview.application.integration.port.ObjectStoragePort;
import com.ruoyi.interview.application.voice.port.ContentDigestPort;
import com.ruoyi.interview.configuration.properties.ProviderProperties;
import com.ruoyi.system.secret.ManagedSecretService;
import com.ruoyi.interview.configuration.properties.VoiceRuntimeProperties;
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
@InterviewEnabled
@Configuration
public class ProviderConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "interview.foundation-safety", name = "object-storage-writes-enabled",
            havingValue = "true")
    LocalFileObjectStorageAdapter localFileObjectStorageAdapter(VoiceRuntimeProperties properties) {
        String configuredRoot = properties.getLocalStorageRoot();
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException(
                    "启用本地音频存储前必须配置 interview.voice-runtime.local-storage-root");
        }
        return new LocalFileObjectStorageAdapter(Path.of(configuredRoot));
    }
    /**
     * 真实 HTTP adapter 仍受 foundation safety gate 控制；Key 缺失时 adapter 自身也只返回稳定失败。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "interview.foundation-safety",
            name = "external-provider-calls-enabled",
            havingValue = "true")
    ChatModelPort deepSeekChatModelAdapter(
            ObjectMapper objectMapper,
            ProviderProperties providerProperties,
            ManagedSecretService managedSecrets
    ) {
        ProviderProperties.DeepSeek deepseek = providerProperties.getDeepseek();
        return new ManagedInterviewProviderAdapters.DeepSeek(
                managedSecrets,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                objectMapper,
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
    SpeechToTextPort volcengineSpeechToTextAdapter(
            ObjectMapper objectMapper,
            ProviderProperties providerProperties,
            AudioArtifactSource artifactSource,
            ManagedSecretService managedSecrets
    ) {
        ProviderProperties.VolcengineSpeech speech = providerProperties.getVolcengineSpeech();
        ProviderProperties.Asr asr = speech.getAsr();
        return new ManagedInterviewProviderAdapters.Asr(
                managedSecrets, providerHttpClient(), objectMapper, artifactSource,
                asr.getModel(), asr.getResourceId(), URI.create(asr.getEndpoint()));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "interview.foundation-safety",
            name = "external-provider-calls-enabled",
            havingValue = "true")
    TextToSpeechPort volcengineTextToSpeechAdapter(
            ObjectMapper objectMapper,
            ProviderProperties providerProperties,
            ManagedSecretService managedSecrets
    ) {
        ProviderProperties.VolcengineSpeech speech = providerProperties.getVolcengineSpeech();
        ProviderProperties.Tts tts = speech.getTts();
        return new ManagedInterviewProviderAdapters.Tts(
                managedSecrets, providerHttpClient(), objectMapper, tts.getModel(),
                tts.getResourceId(), tts.getVoice(), URI.create(tts.getEndpoint()),
                tts.getSampleRate());
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

