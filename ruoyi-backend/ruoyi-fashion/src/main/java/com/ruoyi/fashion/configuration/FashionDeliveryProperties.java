package com.ruoyi.fashion.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 报价交付 Worker 与文件限制；默认仅处理本地私有对象存储。 */
@ConfigurationProperties(prefix = "fashion.delivery")
public class FashionDeliveryProperties {
    private boolean workerEnabled = true;
    private Duration pollDelay = Duration.ofSeconds(2);
    private Duration leaseDuration = Duration.ofMinutes(2);
    private long maximumArtifactBytes = 50L * 1024L * 1024L;

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public Duration getPollDelay() { return pollDelay; }
    public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public long getMaximumArtifactBytes() { return maximumArtifactBytes; }
    public void setMaximumArtifactBytes(long maximumArtifactBytes) { this.maximumArtifactBytes = maximumArtifactBytes; }
}
