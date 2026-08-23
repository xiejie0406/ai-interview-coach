package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.platform.ClaimJobs;
import com.ruoyi.interview.application.platform.ClaimOutboxEvents;
import com.ruoyi.interview.application.platform.FailOutboxEvent;
import com.ruoyi.interview.application.platform.FinishJob;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.PublishOutboxEvent;
import com.ruoyi.interview.application.platform.ReconcileExpiredJobCancellation;
import com.ruoyi.interview.application.platform.RecoverPlatformWork;
import com.ruoyi.interview.application.platform.internal.DefaultClaimJobs;
import com.ruoyi.interview.application.platform.internal.DefaultClaimOutboxEvents;
import com.ruoyi.interview.application.platform.internal.DefaultFailOutboxEvent;
import com.ruoyi.interview.application.platform.internal.DefaultFinishJob;
import com.ruoyi.interview.application.platform.internal.DefaultIdempotencyGuard;
import com.ruoyi.interview.application.platform.internal.DefaultPublishOutboxEvent;
import com.ruoyi.interview.application.platform.internal.DefaultReconcileExpiredJobCancellation;
import com.ruoyi.interview.application.platform.internal.DefaultRecoverPlatformWork;
import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.IdempotencyPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.OutboxPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Wires platform use cases without starting background workers or external publishers. */
@Configuration
public class PlatformUseCaseConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClaimJobs.class)
    ClaimJobs claimJobs(JobPort jobs, DomainEventPort domainEvents, TransactionPort transaction) {
        return new DefaultClaimJobs(jobs, domainEvents, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(FinishJob.class)
    FinishJob finishJob(JobPort jobs, DomainEventPort domainEvents, TransactionPort transaction) {
        return new DefaultFinishJob(jobs, domainEvents, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(ReconcileExpiredJobCancellation.class)
    ReconcileExpiredJobCancellation reconcileExpiredJobCancellation(
            JobPort jobs,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        return new DefaultReconcileExpiredJobCancellation(jobs, domainEvents, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(ClaimOutboxEvents.class)
    ClaimOutboxEvents claimOutboxEvents(OutboxPort outbox, TransactionPort transaction) {
        return new DefaultClaimOutboxEvents(outbox, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(PublishOutboxEvent.class)
    PublishOutboxEvent publishOutboxEvent(OutboxPort outbox, TransactionPort transaction) {
        return new DefaultPublishOutboxEvent(outbox, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(FailOutboxEvent.class)
    FailOutboxEvent failOutboxEvent(OutboxPort outbox, TransactionPort transaction) {
        return new DefaultFailOutboxEvent(outbox, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(RecoverPlatformWork.class)
    RecoverPlatformWork recoverPlatformWork(
            JobPort jobs,
            OutboxPort outbox,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        return new DefaultRecoverPlatformWork(jobs, outbox, domainEvents, transaction);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    IdempotencyGuard idempotencyGuard(
            IdempotencyPort idempotency,
            IdGeneratorPort idGenerator,
            ClockPort clock
    ) {
        return new DefaultIdempotencyGuard(idempotency, idGenerator, clock, Duration.ofHours(24));
    }
}

