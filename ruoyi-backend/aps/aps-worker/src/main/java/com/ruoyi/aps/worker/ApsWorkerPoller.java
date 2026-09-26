package com.ruoyi.aps.worker;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;

/** 三重开关打开后才注册的单实例轮询入口。 */
public final class ApsWorkerPoller
{
    private final ApsWorkerCycle cycle;

    public ApsWorkerPoller(ApsWorkerCycle cycle) { this.cycle = cycle; }

    @Scheduled(fixedDelayString = "${aps.worker.poll-interval-ms:1000}")
    public void poll() { cycle.runOnce(); }

    @PreDestroy
    public void stop() { cycle.requestStop(); }
}
