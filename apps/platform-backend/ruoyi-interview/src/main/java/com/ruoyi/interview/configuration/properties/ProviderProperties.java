package com.ruoyi.interview.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Provider 配置绑定；凭据只从外部配置读取，禁止进入日志或响应。 */
@ConfigurationProperties(prefix = "interview.providers")
public class ProviderProperties {
    private final DeepSeek deepseek = new DeepSeek();
    private final VolcengineSpeech volcengineSpeech = new VolcengineSpeech();
    public DeepSeek getDeepseek() { return deepseek; }
    public VolcengineSpeech getVolcengineSpeech() { return volcengineSpeech; }

    public static class DeepSeek {
        private String apiKey, baseUrl, interviewModel, evaluationModel, thinkingMode;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { baseUrl = value; }
        public String getInterviewModel() { return interviewModel; }
        public void setInterviewModel(String value) { interviewModel = value; }
        public String getEvaluationModel() { return evaluationModel; }
        public void setEvaluationModel(String value) { evaluationModel = value; }
        public String getThinkingMode() { return thinkingMode; }
        public void setThinkingMode(String value) { thinkingMode = value; }
    }

    public static class VolcengineSpeech {
        private String apiKey;
        private final Asr asr = new Asr();
        private final Tts tts = new Tts();
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public Asr getAsr() { return asr; }
        public Tts getTts() { return tts; }
    }

    public static class Asr {
        private String model, fallbackModel, language, endpoint;
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public String getFallbackModel() { return fallbackModel; }
        public void setFallbackModel(String value) { fallbackModel = value; }
        public String getLanguage() { return language; }
        public void setLanguage(String value) { language = value; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String value) { endpoint = value; }
    }

    public static class Tts {
        private String model, voice, language, audioFormat, endpoint;
        private int sampleRate = 24_000;
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public String getVoice() { return voice; }
        public void setVoice(String value) { voice = value; }
        public String getLanguage() { return language; }
        public void setLanguage(String value) { language = value; }
        public String getAudioFormat() { return audioFormat; }
        public void setAudioFormat(String value) { audioFormat = value; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String value) { endpoint = value; }
        public int getSampleRate() { return sampleRate; }
        public void setSampleRate(int value) { sampleRate = value; }
    }
}
