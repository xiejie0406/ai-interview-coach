package com.ruoyi.aden.domain.workspace;

import java.util.List;
import java.util.Optional;

public interface AdenWorkspaceRepository {
    List<AdenWorkspaceMembership> findActiveByUserId(long ruoYiUserId, int limit);

    Optional<AdenWorkspaceMembership> findActiveMembership(AdenWorkspaceId workspaceId, long ruoYiUserId);

    void insertWorkspace(AdenWorkspace workspace);

    void insertInitialOwner(AdenWorkspaceMembership membership);

    void insertAudit(AdenWorkspaceAudit audit);

    void insertWorkspaceForbiddenAudit(AdenWorkspaceForbiddenAudit audit);
}
