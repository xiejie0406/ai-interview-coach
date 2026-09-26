package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.interview.GetInterviewPlan;
import com.ruoyi.interview.application.interview.InterviewPlanView;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

/** 以 RuoYi 当前主体、业务租户和 owner 三重边界读取计划。 */
public final class DefaultGetInterviewPlan implements GetInterviewPlan {

    private final InterviewRepository repository;
    private final ActivePrincipalGuard principal;

    public DefaultGetInterviewPlan(InterviewRepository repository, ActivePrincipalGuard principal) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.principal = java.util.Objects.requireNonNull(principal, "principal");
    }

    @Override
    public InterviewPlanView handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        var plan = repository.findPlan(owner.tenantId(), query.planId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "interview plan was not found", false, Map.of()));
        // 跨用户访问统一伪装为 404，避免枚举计划 id。
        if (!plan.userId().equals(owner.userId())) {
            throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                    "interview plan was not found", false, Map.of());
        }
        return InterviewViews.plan(plan);
    }
}
