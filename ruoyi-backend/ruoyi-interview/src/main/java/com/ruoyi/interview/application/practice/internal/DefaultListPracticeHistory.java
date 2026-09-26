package com.ruoyi.interview.application.practice.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.practice.ListPracticeHistory;
import com.ruoyi.interview.application.practice.PracticeAttemptView;
import com.ruoyi.interview.application.practice.port.PracticeRepository;
import com.ruoyi.interview.application.shared.CursorPage;

public final class DefaultListPracticeHistory implements ListPracticeHistory {

    private final PracticeRepository repository;
    private final ActivePrincipalGuard principal;

    public DefaultListPracticeHistory(PracticeRepository repository, ActivePrincipalGuard principal) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    @Override
    public CursorPage<PracticeAttemptView> handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        PracticeRepository.Page page = repository.findHistory(
                owner.tenantId(), owner.userId(), query.cursor(), query.limit());
        return new CursorPage<>(page.items().stream().map(PracticeViews::view).toList(), page.nextCursor());
    }
}
