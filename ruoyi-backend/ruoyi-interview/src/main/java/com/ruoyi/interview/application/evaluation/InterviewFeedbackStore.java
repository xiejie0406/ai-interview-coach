package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.domain.platform.PrincipalRef;
import java.util.Optional;

public interface InterviewFeedbackStore {
    Optional<InterviewFeedback> find(PrincipalRef owner, String interviewId);
    boolean claim(PrincipalRef owner, String interviewId, String attemptId);
    void publish(PrincipalRef owner, String interviewId, String attemptId, InterviewFeedback report);
    void fail(PrincipalRef owner, String interviewId, String attemptId);
}
