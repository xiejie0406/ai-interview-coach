package com.ruoyi.aden.application.workspace;

import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

public interface AdenWorkspaceSecurityAuditPort {
    void recordForbidden(AdenOperatorPrincipal principal,
                         AdenWorkspaceId requestedWorkspaceId,
                         String correlationId);
}
