package com.ruoyi.modulegate;

import com.ruoyi.interview.configuration.InterviewEnabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewModuleEnabledTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void moduleIsEnabledByDefault() {
        runner.withUserConfiguration(ProbeConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("interviewModuleProbe");
        });
    }

    @Test
    void disablingModuleRemovesAllScannedInterviewComponents() {
        runner.withPropertyValues("interview.enabled=false", "interview.flyway.enabled=true")
                .withUserConfiguration(InterviewComponentScan.class, ProbeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("interviewModuleProbe");
                    assertThat(context).doesNotHaveBean("interviewDataSource");
                    assertThat(context).doesNotHaveBean("interviewFlyway");
                    assertThat(Arrays.stream(context.getBeanDefinitionNames())
                            .map(context::getType)
                            .filter(type -> type != null && type.getName().startsWith("com.ruoyi.interview.")))
                            .isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.ruoyi.interview")
    static class InterviewComponentScan {
    }

    @Configuration(proxyBeanMethods = false)
    @InterviewEnabled
    static class ProbeConfiguration {
        @Bean
        String interviewModuleProbe() {
            return "enabled";
        }
    }
}
