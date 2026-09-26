package com.ruoyi.fashion.configuration;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.fashion.application.agent.run.port.FashionRequirementRuntimePort;
import com.ruoyi.fashion.infrastructure.airuntime.HttpFashionRequirementRuntimeAdapter;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentity;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@FashionModuleEnabled
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FashionAiRuntimeProperties.class)
public class FashionAiRuntimeConfiguration {
    @FashionModuleEnabled
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "fashion.ai-runtime", name = "worker-enabled", havingValue = "true")
    static class WorkerSchedulingConfiguration {
    }

    @Bean
    public FashionRequirementRuntimePort fashionRequirementRuntimePort(
            FashionAiRuntimeProperties properties,
            FashionServiceIdentity identity,
            ObjectMapper objectMapper,
            FashionTelemetry telemetry) {
        return new HttpFashionRequirementRuntimeAdapter(properties, identity, objectMapper, telemetry);
    }
}
