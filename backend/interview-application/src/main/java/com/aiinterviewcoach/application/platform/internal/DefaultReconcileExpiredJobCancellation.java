package com.aiinterviewcoach.application.platform.internal;

import com.aiinterviewcoach.application.platform.ReconcileExpiredJobCancellation;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

public final class DefaultReconcileExpiredJobCancellation implements ReconcileExpiredJobCancellation {

    private final JobPort jobs;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultReconcileExpiredJobCancellation(JobPort jobs, DomainEventPort domainEvents,
                                                  TransactionPort transaction) {
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var actor = command.context().requireServiceActor();
            if (!actor.tenantId().equals(command.context().requireTenantScope())) {
                throw new ApplicationException(ApplicationErrorCode.FORBIDDEN,
                        "service actor tenant scope mismatch", false, Map.of());
            }
            var job = jobs.find(actor.tenantId(), command.jobId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "job was not found", false, Map.of()));
            var eventContext = command.context().eventContext();
            if (command.outcome() == Outcome.CANCELLED_WITHOUT_EFFECT) {
                job.reconcileExpiredCancellationAsCancelled(command.reconciliationReceiptId(),
                        command.context().requestedAt(), command.expectedVersion(), eventContext);
            } else {
                job.reconcileExpiredCancellationAsSucceeded(command.reconciliationReceiptId(),
                        command.context().requestedAt(), command.expectedVersion(), eventContext);
            }
            jobs.save(job);
            domainEvents.append(job.pullDomainEvents());
            return new Result(job.id(), job.state(), job.version());
        });
    }
}
