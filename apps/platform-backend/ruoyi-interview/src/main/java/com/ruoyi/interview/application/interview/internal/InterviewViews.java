package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.interview.InterviewPlanView;
import com.ruoyi.interview.application.interview.InterviewSessionSnapshot;
import com.ruoyi.interview.application.interview.InterviewTurnView;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.interview.InterviewPlan;
import com.ruoyi.interview.domain.interview.InterviewSession;

import java.util.Map;

final class InterviewViews {

    private InterviewViews() {
    }

    static InterviewPlanView plan(InterviewPlan plan) {
        return new InterviewPlanView(plan.id(), plan.planVersionNo(), plan.state(), plan.mode(),
                plan.questions().size(), plan.totalFollowUpBudget(), plan.usageEstimate(),
                plan.usageReservationId(), plan.expiresAt(),
                plan.version());
    }

    static InterviewSessionSnapshot session(
            InterviewSession session,
            InterviewRecoveryProjectionPort.Projection projection
    ) {
        if (!projection.reservation().id().equals(session.planReference().usageReservationId())) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "interview recovery reservation projection is inconsistent", false,
                    Map.of("sessionId", session.id().value()));
        }
        var turns = session.turns().stream().map(turn -> new InterviewTurnView(turn.id(), turn.sequence(),
                turn.kind(), turn.state(), turn.questionPrompt().map(prompt -> prompt.text()),
                turn.answerVersion().map(answer -> answer.id()))).toList();
        return new InterviewSessionSnapshot(
                session.id(),
                session.planReference().planId(),
                session.planReference().planVersionNo(),
                session.planReference().contentHash(),
                session.mode(),
                session.state(),
                turns,
                session.lastStableSequence(),
                projection.pendingJobIds(),
                projection.reservation(),
                projection.report(),
                projection.voiceSummary(),
                session.allowedCommands().stream().toList(),
                projection.streamCursor(),
                session.recoveryExpiresAt(),
                session.failureCode(),
                session.version());
    }
}
