package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.interview.InterviewSessionSnapshot;
import com.aiinterviewcoach.application.interview.port.InterviewRecoveryProjectionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.interview.InterviewSession;
import com.aiinterviewcoach.domain.platform.PrincipalRef;

import java.util.Map;

/** 在已打开的本地事务内装配 tenant + owner scoped 权威恢复快照。 */
final class InterviewSnapshotFactory {

    private final InterviewRecoveryProjectionPort projections;

    InterviewSnapshotFactory(InterviewRecoveryProjectionPort projections) {
        this.projections = java.util.Objects.requireNonNull(projections);
    }

    InterviewSessionSnapshot create(InterviewSession session, PrincipalRef owner) {
        java.util.Objects.requireNonNull(session);
        java.util.Objects.requireNonNull(owner);
        if (!session.tenantId().equals(owner.tenantId()) || !session.userId().equals(owner.userId())) {
            throw notFound();
        }
        var projection = projections.find(owner.tenantId(), owner.userId(), session.id(),
                        session.planReference().usageReservationId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "interview recovery projection was not found", false,
                        Map.of("sessionId", session.id().value())));
        return InterviewViews.session(session, projection);
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "interview session was not found", false, Map.of());
    }
}
