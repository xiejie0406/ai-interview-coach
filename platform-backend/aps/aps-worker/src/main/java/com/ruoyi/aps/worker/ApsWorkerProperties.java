package com.ruoyi.aps.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Worker 专用安全开关。两个专用开关与全局 {@code aps.enabled} 均须显式开启后，
 * 后续阶段才允许注册轮询逻辑。
 */
@ConfigurationProperties(prefix = "aps.worker")
public class ApsWorkerProperties
{
    private boolean enabled;

    private boolean pollingEnabled;

    private long pollIntervalMs = 1000;

    private long staleSolvingTimeoutMs = 300000;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isPollingEnabled()
    {
        return pollingEnabled;
    }

    public void setPollingEnabled(boolean pollingEnabled)
    {
        this.pollingEnabled = pollingEnabled;
    }

    public long getPollIntervalMs() { return pollIntervalMs; }

    public void setPollIntervalMs(long pollIntervalMs)
    {
        if (pollIntervalMs < 250 || pollIntervalMs > 60000)
            throw new IllegalArgumentException("pollIntervalMs 必须在 250～60000 毫秒之间");
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getStaleSolvingTimeoutMs() { return staleSolvingTimeoutMs; }

    public void setStaleSolvingTimeoutMs(long staleSolvingTimeoutMs)
    {
        if (staleSolvingTimeoutMs < 60000 || staleSolvingTimeoutMs > 86400000)
            throw new IllegalArgumentException("staleSolvingTimeoutMs 必须在 60000～86400000 毫秒之间");
        this.staleSolvingTimeoutMs = staleSolvingTimeoutMs;
    }
}
