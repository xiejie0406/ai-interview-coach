package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.interview.RecoverInterview;
import com.aiinterviewcoach.application.interview.port.InterviewRecoveryProjectionPort;
import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** REST 恢复只读取 tenant/owner 作用域内的权威 Session 聚合。 */
public final class DefaultRecoverInterview implements RecoverInterview {

    private final InterviewRepository repository;
    private final ActivePrincipalGuard principal;
    private final InterviewSnapshotFactory snapshots;
    private final TransactionPort transaction;

    public DefaultRecoverInterview(
            InterviewRepository repository,
            ActivePrincipalGuard principal,
            InterviewRecoveryProjectionPort projections,
            TransactionPort transaction
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.snapshots = new InterviewSnapshotFactory(projections);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public com.aiinterviewcoach.application.interview.InterviewSessionSnapshot handle(Query query) {
        return transaction.required(() -> {
            var owner = principal.requireActive(query.context().principal());
            var session = repository.findSession(owner.tenantId(), query.sessionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "interview session was not found", false, Map.of()));
            return snapshots.create(session, owner);
        });
    }
}
