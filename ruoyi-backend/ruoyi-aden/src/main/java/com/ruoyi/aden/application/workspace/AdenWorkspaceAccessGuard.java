package com.ruoyi.aden.application.workspace;

import com.ruoyi.aden.application.error.AdenAccessDeniedException;
import com.ruoyi.aden.application.error.AdenNotFoundException;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/** 功能权限、membership、角色和资源 Workspace 四层门禁。 */
public final class AdenWorkspaceAccessGuard {
    public static final Set<AdenWorkspaceRole> READ_ROLES = Set.of(
            AdenWorkspaceRole.OWNER, AdenWorkspaceRole.OPERATOR, AdenWorkspaceRole.VIEWER);
    public static final Set<AdenWorkspaceRole> WRITE_ROLES = Set.of(
            AdenWorkspaceRole.OWNER, AdenWorkspaceRole.OPERATOR);
    public static final Set<AdenWorkspaceRole> OWNER_ONLY = Set.of(AdenWorkspaceRole.OWNER);

    private static final Logger LOG = LoggerFactory.getLogger(AdenWorkspaceAccessGuard.class);
    private final AdenWorkspaceRepository repository;
    private final AdenWorkspaceSecurityAuditPort securityAudit;

    public AdenWorkspaceAccessGuard(AdenWorkspaceRepository repository,
                                    AdenWorkspaceSecurityAuditPort securityAudit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.securityAudit = Objects.requireNonNull(securityAudit, "securityAudit");
    }

    public AdenWorkspaceMembership requireWorkspace(
            AdenOperatorPrincipal principal,
            String permission,
            AdenWorkspaceId workspaceId,
            Set<AdenWorkspaceRole> allowedRoles,
            String correlationId) {
        requirePermission(principal, permission);
        AdenWorkspaceMembership membership = repository.findActiveMembership(workspaceId, principal.userId())
                .orElseThrow(() -> forbidden(principal, workspaceId, correlationId));
        if (!allowedRoles.contains(membership.role())) throw new AdenAccessDeniedException();
        return membership;
    }

    public void requireResourceWorkspace(AdenOperatorPrincipal principal,
                                         AdenWorkspaceId authorizedWorkspaceId,
                                         AdenWorkspaceId resourceWorkspaceId,
                                         String correlationId) {
        if (!Objects.equals(authorizedWorkspaceId, resourceWorkspaceId)) {
            throw forbidden(principal, authorizedWorkspaceId, correlationId);
        }
    }

    public void requirePermission(AdenOperatorPrincipal principal, String permission) {
        Objects.requireNonNull(principal, "principal");
        if (!principal.hasPermission(permission)) throw new AdenAccessDeniedException();
    }

    private AdenNotFoundException forbidden(AdenOperatorPrincipal principal,
                                            AdenWorkspaceId requestedWorkspaceId,
                                            String correlationId) {
        try {
            securityAudit.recordForbidden(principal, requestedWorkspaceId, correlationId);
        } catch (RuntimeException exception) {
            LOG.error("Aden Workspace 拒绝审计写入失败 correlationId={} actorUserId={} requestedWorkspaceId={} exceptionType={}",
                    correlationId, principal.userId(), requestedWorkspaceId.value(), exception.getClass().getName());
        }
        return new AdenNotFoundException();
    }
}
