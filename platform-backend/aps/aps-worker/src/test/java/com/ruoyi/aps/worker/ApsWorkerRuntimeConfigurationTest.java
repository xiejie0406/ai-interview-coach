package com.ruoyi.aps.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ApsWorkerRuntimeConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApsWorkerRuntimeConfiguration.class);

    @Test
    void remainsDisabledUnlessAllThreeFlagsAreEnabled()
    {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(ApsWorkerRuntimeConfiguration.class));

        contextRunner.withPropertyValues("aps.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ApsWorkerRuntimeConfiguration.class));

        contextRunner.withPropertyValues(
                        "aps.enabled=true",
                        "aps.worker.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ApsWorkerRuntimeConfiguration.class));

        contextRunner.withPropertyValues(
                        "aps.enabled=true",
                        "aps.worker.polling-enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ApsWorkerRuntimeConfiguration.class));
    }

    @Test
    void activatesOnlyWhenAllThreeFlagsAreEnabled()
    {
        contextRunner.withPropertyValues(
                        "aps.enabled=true",
                        "aps.worker.enabled=true",
                        "aps.worker.polling-enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(ApsWorkerRuntimeConfiguration.class));
    }
}
