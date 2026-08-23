package com.aiinterviewcoach.application.learning.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.learning.LearningPlanRepository;
import com.aiinterviewcoach.application.learning.ListLearningPlans;

/** Owner-scoped cursor query；cursor 的解析、不透明编码与 owner 校验由 persistence owner 负责。 */
public final class DefaultListLearningPlans implements ListLearningPlans {

    private final LearningPlanRepository repository;
    private final ActivePrincipalGuard principal;

    public DefaultListLearningPlans(LearningPlanRepository repository, ActivePrincipalGuard principal) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    @Override
    public Result handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        LearningPlanRepository.Page page = repository.findByOwner(
                owner.tenantId(), owner.userId(), query.cursor(), query.limit());
        return new Result(page.items(), page.nextCursor());
    }
}
