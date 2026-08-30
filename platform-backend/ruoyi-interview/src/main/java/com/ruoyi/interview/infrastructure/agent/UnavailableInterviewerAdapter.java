package com.ruoyi.interview.infrastructure.agent;

import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import com.ruoyi.interview.application.agent.interview.InterviewAgentInput;
import com.ruoyi.interview.application.agent.interview.InterviewerCandidateAction;
import com.ruoyi.interview.application.agent.interview.InterviewerPort;
import com.ruoyi.interview.application.agent.port.InvocationContext;

/** 当前输入只有 QuestionVersion 引用而无受控题目正文；禁止模型凭 ID 生成或猜写面试题。 */
public final class UnavailableInterviewerAdapter implements InterviewerPort {

    @Override
    public InterviewerCandidateAction propose(InterviewAgentInput input, InvocationContext context) {
        java.util.Objects.requireNonNull(input, "interviewAgentInput");
        java.util.Objects.requireNonNull(context, "invocationContext");
        throw new AdapterUnavailableException("interviewer-question-content-source");
    }
}


