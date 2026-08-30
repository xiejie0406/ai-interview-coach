package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.interview.RunInterviewStep;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

/** 当前公共契约没有 Agent 路由、预算、ActionGuard 与候选回执端口，安全默认拒绝执行。 */
public final class DefaultRunInterviewStep implements RunInterviewStep {

    @Override
    public com.ruoyi.interview.application.interview.InterviewSessionSnapshot handle(Command command) {
        throw new ApplicationException(ApplicationErrorCode.CAPABILITY_UNAVAILABLE,
                "interview step execution requires approved Agent route and ActionGuard ports", false, Map.of());
    }
}
