package com.ruoyi.aps.worker;

import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.planning.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
                .withBean(PlanRepository.class, () -> mock(PlanRepository.class))
                .withBean(ApsTransactionOperations.class, () -> mock(ApsTransactionOperations.class))
                .withBean(ApsWorkerProperties.class, ApsWorkerProperties::new)
                .run(context -> assertThat(context)
                        .hasSingleBean(ApsWorkerRuntimeConfiguration.class)
                        .hasSingleBean(ApsWorkerCycle.class)
                        .hasSingleBean(ApsWorkerPoller.class));
    }
}
