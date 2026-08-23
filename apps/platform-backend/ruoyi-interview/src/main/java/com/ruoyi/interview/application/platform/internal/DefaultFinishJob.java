package com.ruoyi.interview.application.platform.internal;

import com.ruoyi.interview.application.platform.FinishJob;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

/** Job effect durable 后才能成功/失败；重新校验 tenant、lease、attempt 和 aggregate version。 */
public final class DefaultFinishJob implements FinishJob {

    private final JobPort jobs;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultFinishJob(JobPort jobs, DomainEventPort domainEvents, TransactionPort transaction) {
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var execution = command.execution();
            var job = jobs.find(execution.tenantId(), execution.jobId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "job was not found", false, Map.of()));
            job.requireExecutionLease(execution.workerId(), execution.attemptNo(), command.finishedAt(),
                    execution.expectedJobVersion());
            var context = new com.ruoyi.interview.domain.platform.EventContext(execution.correlationId(),
                    command.finishedAt());
            if (command.succeeded()) {
                job.succeed(execution.workerId(), execution.attemptNo(), command.finishedAt(),
                        execution.expectedJobVersion(), context);
            } else {
                job.fail(execution.workerId(), execution.attemptNo(), command.finishedAt(),
                        command.failure().orElseThrow(), execution.expectedJobVersion(), context);
            }
            jobs.save(job);
            domainEvents.append(job.pullDomainEvents());
            return new Result(job.id(), job.state(), job.version());
        });
    }
}
