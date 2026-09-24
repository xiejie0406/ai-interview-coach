package com.ruoyi.fashion.application.agent.worker;

import com.ruoyi.fashion.application.agent.run.FashionRequirementRunService;
import com.ruoyi.fashion.configuration.FashionAiRuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界进程内 Worker；默认关闭，不会在未配置 Runtime 时轮询业务库。 */
@Component
@ConditionalOnProperty(prefix = "fashion.ai-runtime", name = "worker-enabled", havingValue = "true")
public class FashionRequirementRunWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(FashionRequirementRunWorker.class);

    private final FashionRequirementRunService service;
    private final FashionAiRuntimeProperties properties;

    public FashionRequirementRunWorker(
            FashionRequirementRunService service,
            FashionAiRuntimeProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fashion.ai-runtime.poll-delay:PT2S}")
    public void poll() {
        try {
            service.processOne(properties.getWorkerId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Fashion requirement worker poll failed: type={}", exception.getClass().getSimpleName());
        }
    }
}
