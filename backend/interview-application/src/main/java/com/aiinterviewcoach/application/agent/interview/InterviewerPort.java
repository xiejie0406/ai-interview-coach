package com.aiinterviewcoach.application.agent.interview;

import com.aiinterviewcoach.application.agent.port.InvocationContext;

/** Agent 只能返回候选；不得持有 InterviewRepository 或领域聚合。 */
@FunctionalInterface
public interface InterviewerPort {

    InterviewerCandidateAction propose(InterviewAgentInput input, InvocationContext context);
}
