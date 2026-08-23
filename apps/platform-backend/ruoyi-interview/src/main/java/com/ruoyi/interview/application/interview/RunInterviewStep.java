package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.platform.JobExecutionContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

/**
 * Worker 内部用例：先在业务事务中加载 Job 并调用 requireExecutionLease，再取得 Agent 候选、执行
 * ActionGuard，最后由 Session 聚合提交问题或完成命令。过期 Worker 不得写 Session。
 */
@FunctionalInterface
public interface RunInterviewStep {

    InterviewSessionSnapshot handle(Command command);

    record Command(
            JobExecutionContext execution,
            ResourceId sessionId
    ) {
        public Command {
            DomainPreconditions.requireNonNull(execution, "jobExecutionContext");
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
        }
    }
}
