package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.RecoverInterview;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

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
    public com.ruoyi.interview.application.interview.InterviewSessionSnapshot handle(Query query) {
        return transaction.required(() -> {
            var owner = principal.requireActive(query.context().principal());
            var session = repository.findSession(owner.tenantId(), query.sessionId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "interview session was not found", false, Map.of()));
            return snapshots.create(session, owner);
        });
    }
}
