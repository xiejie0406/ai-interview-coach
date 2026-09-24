package com.ruoyi.aps.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ApsApiConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApsApiConfiguration.class);

    @Test
    void remainsDisabledByDefault()
    {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ApsApiConfiguration.class));
    }

    @Test
    void remainsDisabledWhenOnlyGlobalFlagIsEnabled()
    {
        contextRunner.withPropertyValues("aps.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ApsApiConfiguration.class));
    }

    @Test
    void remainsDisabledWhenOnlyApiFlagIsEnabled()
    {
        contextRunner.withPropertyValues("aps.api.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ApsApiConfiguration.class));
    }

    @Test
    void requiresBothFlagsToBeEnabledExplicitly()
    {
        contextRunner.withPropertyValues("aps.enabled=true", "aps.api.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ApsApiConfiguration.class));
    }
}
