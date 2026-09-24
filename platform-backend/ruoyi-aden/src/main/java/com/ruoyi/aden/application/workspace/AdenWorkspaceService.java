package com.ruoyi.aden.application.workspace;

import com.ruoyi.aden.application.error.AdenAccessDeniedException;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.workspace.AdenWorkspace;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMemberStatus;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRole;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class AdenWorkspaceService {
    public static final String LIST_PERMISSION = "aden:workspace:list";
    public static final String CREATE_PERMISSION = "aden:workspace:create";

    private final AdenWorkspaceRepository repository;
    private final AdenIdGenerator idGenerator;
    private final Clock clock;

    public AdenWorkspaceService(AdenWorkspaceRepository repository, AdenIdGenerator idGenerator, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(transactionManager = "adenTransactionManager", readOnly = true)
    public List<AdenWorkspaceMembership> list(AdenOperatorPrincipal principal) {
        requirePermission(principal, LIST_PERMISSION);
        return List.copyOf(repository.findActiveByUserId(principal.userId(), 100));
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public AdenWorkspaceMembership create(
            AdenOperatorPrincipal principal,
            String displayName,
            String correlationId) {
        requirePermission(principal, CREATE_PERMISSION);
        Instant now = clock.instant();
        AdenWorkspace workspace = new AdenWorkspace(
                new AdenWorkspaceId(idGenerator.nextId()),
                displayName,
                AdenWorkspaceStatus.ACTIVE,
                0,
                principal.userId(),
                now,
                now);
        AdenWorkspaceMembership owner = new AdenWorkspaceMembership(
                workspace,
                principal.userId(),
                AdenWorkspaceRole.OWNER,
                AdenWorkspaceMemberStatus.ACTIVE,
                now);
        repository.insertWorkspace(workspace);
        repository.insertInitialOwner(owner);
        repository.insertAudit(new AdenWorkspaceAudit(
                idGenerator.nextId(),
                workspace.id(),
                "WORKSPACE_CREATED",
                workspace.id().value(),
                principal.userId(),
                Objects.requireNonNull(correlationId, "correlationId"),
                now));
        return owner;
    }

    private static void requirePermission(AdenOperatorPrincipal principal, String permission) {
        Objects.requireNonNull(principal, "principal");
        if (!principal.hasPermission(permission)) throw new AdenAccessDeniedException();
    }
}
