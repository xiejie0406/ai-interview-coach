package com.aiinterviewcoach.application.platform.internal;

import com.aiinterviewcoach.application.platform.ClaimJobs;
import com.aiinterviewcoach.application.platform.JobExecutionContext;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;

import java.util.ArrayList;

/** Worker claim 用例；adapter 必须保证 findClaimable 的数据库原子 claim 语义。 */
public final class DefaultClaimJobs implements ClaimJobs {

    private final JobPort jobs;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultClaimJobs(JobPort jobs, DomainEventPort domainEvents, TransactionPort transaction) {
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public java.util.List<ClaimedJob> handle(Command command) {
        return transaction.required(() -> {
            var result = new ArrayList<ClaimedJob>();
            for (var job : jobs.findClaimable(command.jobType(), command.now(), command.limit())) {
                int attempt = job.claim(command.workerId(), command.now(), command.leaseDuration(), job.version(),
                        new com.aiinterviewcoach.domain.platform.EventContext(command.correlationId(), command.now()));
                jobs.save(job);
                domainEvents.append(job.pullDomainEvents());
                result.add(new ClaimedJob(new JobExecutionContext(job.tenantId(), job.id(), command.workerId(),
                        attempt, job.version(), job.leaseExpiresAt().orElseThrow(), command.correlationId()),
                        job.jobType(), job.businessOperationId(), job.payloadReferences()));
            }
            return result;
        });
    }
}
