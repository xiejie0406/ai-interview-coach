package com.ruoyi.interview.configuration;

import com.ruoyi.interview.infrastructure.job.UnavailableJobAdapter;
import com.ruoyi.interview.application.platform.port.JobPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅装配 Foundation 占位；不启动 scheduler、Worker 线程、队列或 lease 循环。 */
@InterviewEnabled
@Configuration
public class WorkerConfiguration {
    @Bean
    @ConditionalOnMissingBean(JobPort.class)
    UnavailableJobAdapter unavailableJobAdapter() {
        return new UnavailableJobAdapter();
    }
}

