package com.aiinterviewcoach.application.practice.internal;

import com.aiinterviewcoach.application.practice.PracticeAttemptView;
import com.aiinterviewcoach.domain.practice.PracticeAttempt;

final class PracticeViews {

    private PracticeViews() {
    }

    static PracticeAttemptView view(PracticeAttempt attempt) {
        var latest = attempt.answerVersions().stream().reduce((first, second) -> second);
        return new PracticeAttemptView(attempt.id(), attempt.questionVersion(), attempt.rubricVersion(),
                attempt.state(), attempt.version(), latest.map(answer -> answer.id()),
                java.util.Optional.ofNullable(attempt.submittedAt()));
    }
}
