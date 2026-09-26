package com.ruoyi.aden.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdenWorkspaceMapper {
    List<AdenWorkspaceRow> selectActiveByUserId(@Param("ruoYiUserId") long ruoYiUserId,
                                                 @Param("limit") int limit);

    AdenWorkspaceRow selectActiveMembership(@Param("workspaceId") String workspaceId,
                                             @Param("ruoYiUserId") long ruoYiUserId);

    int insertWorkspace(@Param("workspaceId") String workspaceId,
                        @Param("workspaceName") String workspaceName,
                        @Param("workspaceStatus") String workspaceStatus,
                        @Param("version") long version,
                        @Param("createdByRuoYiUserId") long createdByRuoYiUserId,
                        @Param("createdAt") LocalDateTime createdAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    int insertMember(@Param("workspaceId") String workspaceId,
                     @Param("ruoYiUserId") long ruoYiUserId,
                     @Param("workspaceRole") String workspaceRole,
                     @Param("memberStatus") String memberStatus,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int insertAudit(@Param("workspaceId") String workspaceId,
                    @Param("auditEventId") String auditEventId,
                    @Param("actionCode") String actionCode,
                    @Param("resourceId") String resourceId,
                    @Param("actorId") String actorId,
                    @Param("correlationId") String correlationId,
                    @Param("occurredAt") LocalDateTime occurredAt,
                    @Param("createdAt") LocalDateTime createdAt);

    int insertWorkspaceForbiddenAudit(@Param("auditEventId") String auditEventId,
                                      @Param("requestedWorkspaceId") String requestedWorkspaceId,
                                      @Param("actorId") String actorId,
                                      @Param("correlationId") String correlationId,
                                      @Param("occurredAt") LocalDateTime occurredAt,
                                      @Param("createdAt") LocalDateTime createdAt);
}
