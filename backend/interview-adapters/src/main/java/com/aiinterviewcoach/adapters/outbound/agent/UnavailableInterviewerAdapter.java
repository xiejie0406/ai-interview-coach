package com.aiinterviewcoach.adapters.outbound.agent;

import com.aiinterviewcoach.adapters.outbound.AdapterUnavailableException;
import com.aiinterviewcoach.application.agent.interview.InterviewAgentInput;
import com.aiinterviewcoach.application.agent.interview.InterviewerCandidateAction;
import com.aiinterviewcoach.application.agent.interview.InterviewerPort;
import com.aiinterviewcoach.application.agent.port.InvocationContext;

/** 当前输入只有 QuestionVersion 引用而无受控题目正文；禁止模型凭 ID 生成或猜写面试题。 */
public final class UnavailableInterviewerAdapter implements InterviewerPort {

    @Override
    public InterviewerCandidateAction propose(InterviewAgentInput input, InvocationContext context) {
        java.util.Objects.requireNonNull(input, "interviewAgentInput");
        java.util.Objects.requireNonNull(context, "invocationContext");
        throw new AdapterUnavailableException("interviewer-question-content-source");
    }
}
