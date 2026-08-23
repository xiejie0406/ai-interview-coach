package com.aiinterviewcoach.application.learning.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.learning.GetLearningPlan;
import com.aiinterviewcoach.application.learning.LearningPlanRepository;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** Reads a learning plan within the authenticated tenant and owner scope. */
public final class DefaultGetLearningPlan implements GetLearningPlan {

    private final LearningPlanRepository repository;
    private final ActivePrincipalGuard principal;

    public DefaultGetLearningPlan(LearningPlanRepository repository, ActivePrincipalGuard principal) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    @Override
    public Result handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        var plan = repository.find(owner.tenantId(), query.learningPlanId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "learning plan was not found", false, Map.of()));
        if (!plan.userId().equals(owner.userId())) {
            throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                    "learning plan was not found", false, Map.of());
        }
        return new Result(plan);
    }
}
