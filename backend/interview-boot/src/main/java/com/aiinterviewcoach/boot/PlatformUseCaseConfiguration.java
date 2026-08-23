package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.application.platform.ClaimJobs;
import com.aiinterviewcoach.application.platform.ClaimOutboxEvents;
import com.aiinterviewcoach.application.platform.FailOutboxEvent;
import com.aiinterviewcoach.application.platform.FinishJob;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.PublishOutboxEvent;
import com.aiinterviewcoach.application.platform.ReconcileExpiredJobCancellation;
import com.aiinterviewcoach.application.platform.RecoverPlatformWork;
import com.aiinterviewcoach.application.platform.internal.DefaultClaimJobs;
import com.aiinterviewcoach.application.platform.internal.DefaultClaimOutboxEvents;
import com.aiinterviewcoach.application.platform.internal.DefaultFailOutboxEvent;
import com.aiinterviewcoach.application.platform.internal.DefaultFinishJob;
import com.aiinterviewcoach.application.platform.internal.DefaultIdempotencyGuard;
import com.aiinterviewcoach.application.platform.internal.DefaultPublishOutboxEvent;
import com.aiinterviewcoach.application.platform.internal.DefaultReconcileExpiredJobCancellation;
import com.aiinterviewcoach.application.platform.internal.DefaultRecoverPlatformWork;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.IdempotencyPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.platform.port.OutboxPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.boot.properties.RuntimeSecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            ClockPort clock,
            RuntimeSecurityProperties properties
    ) {
        return new DefaultIdempotencyGuard(idempotency, idGenerator, clock, properties.getIdempotencyTtl());
    }
}
