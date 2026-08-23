package com.aiinterviewcoach.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部模型与语音供应商的非运行时配置契约。
 *
 * <p>本对象不记录或输出凭据；是否允许真实调用仍由 foundation safety gate 和具体 adapter 决定。</p>
 */
@ConfigurationProperties(prefix = "interview.providers")
public class ProviderProperties {

    private final DeepSeek deepseek = new DeepSeek();
    private final VolcengineSpeech volcengineSpeech = new VolcengineSpeech();

    public DeepSeek getDeepseek() {
        return deepseek;
    }

    public VolcengineSpeech getVolcengineSpeech() {
        return volcengineSpeech;
    }

    public static class DeepSeek {
        private String apiKey;
        private String baseUrl;
        private String interviewModel;
        private String evaluationModel;
        private String thinkingMode;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getInterviewModel() { return interviewModel; }
        public void setInterviewModel(String interviewModel) { this.interviewModel = interviewModel; }
        public String getEvaluationModel() { return evaluationModel; }
        public void setEvaluationModel(String evaluationModel) { this.evaluationModel = evaluationModel; }
        public String getThinkingMode() { return thinkingMode; }
        public void setThinkingMode(String thinkingMode) { this.thinkingMode = thinkingMode; }
    }

    public static class VolcengineSpeech {
        private String apiKey;
        private final Asr asr = new Asr();
        private final Tts tts = new Tts();

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Asr getAsr() { return asr; }
        public Tts getTts() { return tts; }
    }

    public static class Asr {
        private String model;
        private String fallbackModel;
        private String language;
        private String endpoint;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getFallbackModel() { return fallbackModel; }
        public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    }

    public static class Tts {
        private String model;
        private String voice;
        private String language;
        private String audioFormat;
        private String endpoint;
        private int sampleRate = 24_000;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getVoice() { return voice; }
        public void setVoice(String voice) { this.voice = voice; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getAudioFormat() { return audioFormat; }
        public void setAudioFormat(String audioFormat) { this.audioFormat = audioFormat; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public int getSampleRate() { return sampleRate; }
        public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }
    }
}
