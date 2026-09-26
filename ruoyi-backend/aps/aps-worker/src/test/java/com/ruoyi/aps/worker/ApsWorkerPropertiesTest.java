package com.ruoyi.aps.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ApsWorkerPropertiesTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void doesNotEnableWorkerOrPollingByDefault()
    {
        contextRunner.run(context -> {
            ApsWorkerProperties properties = context.getBean(ApsWorkerProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.isPollingEnabled()).isFalse();
            assertThat(properties.getStaleSolvingTimeoutMs()).isEqualTo(300000);
        });
    }

    @Test
    void requiresExplicitFlags()
    {
        contextRunner.withPropertyValues(
                        "aps.worker.enabled=true",
                        "aps.worker.polling-enabled=true",
                        "aps.worker.stale-solving-timeout-ms=600000")
                .run(context -> {
                    ApsWorkerProperties properties = context.getBean(ApsWorkerProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isPollingEnabled()).isTrue();
                    assertThat(properties.getStaleSolvingTimeoutMs()).isEqualTo(600000);
                });
    }

    @Test
    void rejectsUnsafeStaleSolvingTimeout()
    {
        contextRunner.withPropertyValues("aps.worker.stale-solving-timeout-ms=1000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApsWorkerProperties.class)
    static class PropertiesConfiguration
    {
    }
}
