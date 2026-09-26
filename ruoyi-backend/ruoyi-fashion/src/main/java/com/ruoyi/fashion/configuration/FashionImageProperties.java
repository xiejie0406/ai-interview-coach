package com.ruoyi.fashion.configuration;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 图片 Worker 默认可处理本地样例/拼版；真实 Provider 默认关闭。 */
@ConfigurationProperties(prefix = "fashion.image")
public class FashionImageProperties {
    private boolean workerEnabled = true;
    private boolean providerEnabled;
    private Duration pollDelay = Duration.ofSeconds(2);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private BigDecimal estimatedCostPerResult = new BigDecimal("1.00");

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public boolean isProviderEnabled() { return providerEnabled; }
    public void setProviderEnabled(boolean providerEnabled) { this.providerEnabled = providerEnabled; }
    public Duration getPollDelay() { return pollDelay; }
    public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public BigDecimal getEstimatedCostPerResult() { return estimatedCostPerResult; }
    public void setEstimatedCostPerResult(BigDecimal estimatedCostPerResult) {
        this.estimatedCostPerResult = estimatedCostPerResult;
    }
}
