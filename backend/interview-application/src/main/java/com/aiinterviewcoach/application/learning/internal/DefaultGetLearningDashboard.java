package com.aiinterviewcoach.application.learning.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.learning.GetLearningDashboard;
import com.aiinterviewcoach.application.learning.LearningDashboardPort;

/** 返回显式 fresh/stale/failed 状态的 owner-scoped dashboard 投影。 */
public final class DefaultGetLearningDashboard implements GetLearningDashboard {

    private final LearningDashboardPort dashboard;
    private final ActivePrincipalGuard principal;

    public DefaultGetLearningDashboard(LearningDashboardPort dashboard, ActivePrincipalGuard principal) {
        this.dashboard = java.util.Objects.requireNonNull(dashboard);
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    @Override
    public Result handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        return new Result(dashboard.load(owner.tenantId(), owner.userId()));
    }
}
