package com.ruoyi.fashion.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime 地址和 Worker 只能由部署配置注入，默认不启动外部调用。 */
@ConfigurationProperties(prefix = "fashion.ai-runtime")
public class FashionAiRuntimeProperties {
    private String baseUrl;
    private boolean workerEnabled;
    private String workerId = "ruoyi-fashion-local";
    private Duration pollDelay = Duration.ofSeconds(2);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(5);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public Duration getPollDelay() { return pollDelay; }
    public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
}
