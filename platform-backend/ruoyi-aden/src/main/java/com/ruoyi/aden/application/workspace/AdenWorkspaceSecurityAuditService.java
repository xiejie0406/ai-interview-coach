package com.ruoyi.aden.application.workspace;

import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceForbiddenAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/** 使用独立事务保存拒绝审计，避免随后抛出的 404 回滚审计事实。 */
public class AdenWorkspaceSecurityAuditService implements AdenWorkspaceSecurityAuditPort {
    private final AdenWorkspaceRepository repository;
    private final AdenIdGenerator idGenerator;
    private final Clock clock;

    public AdenWorkspaceSecurityAuditService(AdenWorkspaceRepository repository,
                                             AdenIdGenerator idGenerator,
                                             Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(transactionManager = "adenTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordForbidden(AdenOperatorPrincipal principal,
                                AdenWorkspaceId requestedWorkspaceId,
                                String correlationId) {
        Objects.requireNonNull(principal, "principal");
        repository.insertWorkspaceForbiddenAudit(new AdenWorkspaceForbiddenAudit(
                idGenerator.nextId(), requestedWorkspaceId, principal.userId(),
                Objects.requireNonNull(correlationId, "correlationId"), clock.instant()));
    }
}
