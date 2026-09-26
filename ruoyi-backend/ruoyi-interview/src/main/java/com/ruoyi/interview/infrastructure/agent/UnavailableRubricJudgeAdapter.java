package com.ruoyi.interview.infrastructure.agent;

import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import com.ruoyi.interview.application.agent.judge.RubricJudgePort;

/** 当前请求没有 owner-scoped Rubric 正文引用；拒绝让模型只按 rubricVersionId 猜评分标准。 */
public final class UnavailableRubricJudgeAdapter implements RubricJudgePort {

    @Override
    public Result judge(Request request) {
        java.util.Objects.requireNonNull(request, "rubricJudgeRequest");
        throw new AdapterUnavailableException("rubric-judge-content-source");
    }
}


