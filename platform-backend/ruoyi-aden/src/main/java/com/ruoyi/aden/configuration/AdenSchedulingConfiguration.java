package com.ruoyi.aden.configuration;

import com.ruoyi.aden.application.event.AdenOutboxRecoveryService;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.infrastructure.outbox.AdenOutboxPublisher;
import com.ruoyi.aden.infrastructure.stream.AdenWorkspaceEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/** Aden 自有有界执行器与维护调度，不覆盖共享 Web MVC async executor。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true")
public class AdenSchedulingConfiguration {
    @Bean(name = "adenStreamExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor adenStreamExecutor(AdenProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("aden-stream-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(properties.getStream().getPerConnectionQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean(name = "adenSseSendExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor adenSseSendExecutor(AdenProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("aden-sse-send-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(properties.getStream().getPerConnectionQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean(name = "adenScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler adenScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("aden-scheduler-");
        scheduler.setPoolSize(2);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public AdenWorkspaceEventBroadcaster adenWorkspaceEventBroadcaster(
            AdenOperatorQueryService queries,
            @Qualifier("adenStreamExecutor") ThreadPoolTaskExecutor drainExecutor,
            @Qualifier("adenSseSendExecutor") ThreadPoolTaskExecutor sendExecutor,
            AdenProperties properties) {
        return new AdenWorkspaceEventBroadcaster(queries, drainExecutor, sendExecutor,
                properties.getStream().getReplayBatchSize(),
                properties.getStream().getPerConnectionQueueCapacity(),
                properties.getStream().getSendTimeoutSeconds(),
                properties.getStream().getConnectionTtlSeconds());
    }

    @Bean
    public AdenOutboxPublisher adenOutboxPublisher(AdenOutboxRecoveryService outbox,
                                                   AdenWorkspaceEventBroadcaster broadcaster) {
        return new AdenOutboxPublisher(outbox, broadcaster);
    }

    @Bean
    @Lazy(false)
    public AdenMaintenanceCoordinator adenMaintenanceCoordinator(
            AdenOutboxPublisher publisher,
            AdenWorkspaceEventBroadcaster broadcaster,
            @Qualifier("adenScheduler") TaskScheduler scheduler,
            AdenProperties properties) {
        return new AdenMaintenanceCoordinator(publisher, broadcaster, scheduler,
                Duration.ofMillis(properties.getOutbox().getRetry().getInitialDelayMilliseconds()),
                Duration.ofSeconds(properties.getStream().getHeartbeatIntervalSeconds()));
    }

    public static final class AdenMaintenanceCoordinator implements InitializingBean, DisposableBean {
        private static final Logger LOG = LoggerFactory.getLogger(AdenMaintenanceCoordinator.class);
        private final AdenOutboxPublisher publisher;
        private final AdenWorkspaceEventBroadcaster broadcaster;
        private final TaskScheduler scheduler;
        private final Duration outboxInterval;
        private final Duration heartbeatInterval;
        private final List<ScheduledFuture<?>> futures = new ArrayList<>();

        AdenMaintenanceCoordinator(AdenOutboxPublisher publisher,
                                   AdenWorkspaceEventBroadcaster broadcaster,
                                   TaskScheduler scheduler,
                                   Duration outboxInterval,
                                   Duration heartbeatInterval) {
            this.publisher=publisher; this.broadcaster=broadcaster; this.scheduler=scheduler;
            this.outboxInterval=outboxInterval; this.heartbeatInterval=heartbeatInterval;
        }

        @Override public void afterPropertiesSet() {
            futures.add(scheduler.scheduleWithFixedDelay(this::publish, outboxInterval));
            futures.add(scheduler.scheduleWithFixedDelay(this::heartbeat, heartbeatInterval));
        }

        private void publish() {
            try { publisher.publishOnce(100); }
            catch (RuntimeException exception) {
                LOG.warn("Aden Outbox 周期领取失败 exceptionType={}", exception.getClass().getName());
            }
        }

        private void heartbeat() {
            try { broadcaster.heartbeatAll(); }
            catch (RuntimeException exception) {
                LOG.warn("Aden SSE heartbeat 调度失败 exceptionType={}", exception.getClass().getName());
            }
        }

        @Override public void destroy() {
            futures.forEach(future -> future.cancel(false));
            futures.clear();
        }
    }
}
