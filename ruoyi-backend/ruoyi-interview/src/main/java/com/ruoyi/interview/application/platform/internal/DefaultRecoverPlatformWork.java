package com.ruoyi.interview.application.platform.internal;

import com.ruoyi.interview.application.platform.RecoverPlatformWork;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.OutboxPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.domain.platform.EventContext;

public final class DefaultRecoverPlatformWork implements RecoverPlatformWork {

    private final JobPort jobs;
    private final OutboxPort outbox;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultRecoverPlatformWork(JobPort jobs, OutboxPort outbox,
                                      DomainEventPort domainEvents, TransactionPort transaction) {
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.outbox = java.util.Objects.requireNonNull(outbox);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            EventContext context = new EventContext(command.correlationId(), command.now());
            int retryable = 0;
            for (var job : jobs.findRetryable(command.now(), command.jobLimit())) {
                job.makePending(command.now(), job.version(), context);
                jobs.save(job);
                domainEvents.append(job.pullDomainEvents());
                retryable++;
            }
            int remaining = Math.max(0, command.jobLimit() - retryable);
            int leases = 0;
            if (remaining > 0) {
                for (var job : jobs.findExpiredRunningLeases(command.now(), remaining)) {
                    job.reclaimExpiredLease(command.now(), job.version(), context);
                    jobs.save(job);
                    domainEvents.append(job.pullDomainEvents());
                    leases++;
                }
            }
            int claims = 0;
            for (var event : outbox.findExpiredClaims(command.now(), command.outboxLimit())) {
                event.reclaimExpiredClaim(command.now(), event.version());
                outbox.save(event);
                claims++;
            }
            return new Result(retryable, leases, claims);
        });
    }
}
