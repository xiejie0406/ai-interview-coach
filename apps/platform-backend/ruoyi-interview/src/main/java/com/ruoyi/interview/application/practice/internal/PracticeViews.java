package com.ruoyi.interview.application.practice.internal;

import com.ruoyi.interview.application.practice.PracticeAttemptView;
import com.ruoyi.interview.domain.practice.PracticeAttempt;

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
