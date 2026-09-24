package com.ruoyi.aden.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AdenRunnerMapper {
    Long lockWorkspace(@Param("workspaceId") String workspaceId);
    int insertRunner(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId,@Param("name")String name,@Param("capabilitiesJson")String capabilitiesJson,@Param("credentialEpoch")long credentialEpoch,@Param("userId")long userId,@Param("now")LocalDateTime now);
    int insertCredential(@Param("workspaceId")String workspaceId,@Param("credentialId")String credentialId,@Param("runnerId")String runnerId,@Param("epoch")long epoch,@Param("digest")String digest,@Param("pepperKeyId")String pepperKeyId,@Param("issuedAt")LocalDateTime issuedAt,@Param("expiresAt")LocalDateTime expiresAt);
    AdenRunnerRow selectRunnerForUpdate(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId);
    AdenRunnerCredentialRow selectCredentialForUpdate(@Param("workspaceId")String workspaceId,@Param("credentialId")String credentialId);
    List<AdenRunnerCredentialRow> selectCredentialByPublicId(@Param("credentialId")String credentialId);
    List<AdenRunnerSessionRow> selectSessionByPublicId(@Param("sessionId")String sessionId);
    int advanceRunnerSession(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId,@Param("expectedEpoch")long expectedEpoch,@Param("expectedVersion")long expectedVersion,@Param("nextEpoch")long nextEpoch,@Param("now")LocalDateTime now);
    int supersedeSessions(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId,@Param("now")LocalDateTime now);
    int insertSession(@Param("workspaceId")String workspaceId,@Param("sessionId")String sessionId,@Param("runnerId")String runnerId,@Param("credentialId")String credentialId,@Param("epoch")long epoch,@Param("digest")String digest,@Param("pepperKeyId")String pepperKeyId,@Param("capacity")int capacity,@Param("metadataJson")String metadataJson,@Param("heartbeatAt")LocalDateTime heartbeatAt,@Param("expiresAt")LocalDateTime expiresAt);
    int revokeRunner(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId,@Param("expectedVersion")long expectedVersion,@Param("now")LocalDateTime now);
    int revokeCredentials(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId,@Param("now")LocalDateTime now);
    int revokeSessions(@Param("workspaceId")String workspaceId,@Param("runnerId")String runnerId,@Param("now")LocalDateTime now);
    int advanceWorkspace(@Param("workspaceId")String workspaceId,@Param("expected")long expected,@Param("next")long next,@Param("now")LocalDateTime now);
    int insertEvent(@Param("workspaceId")String workspaceId,@Param("eventId")String eventId,@Param("sequence")long sequence,@Param("runnerId")String runnerId,@Param("version")long version,@Param("eventType")String eventType,@Param("payload")String payload,@Param("correlationId")String correlationId,@Param("actorType")String actorType,@Param("actorId")String actorId,@Param("now")LocalDateTime now);
    int insertOutbox(@Param("workspaceId")String workspaceId,@Param("outboxId")String outboxId,@Param("eventId")String eventId,@Param("now")LocalDateTime now);
    int insertAudit(@Param("workspaceId")String workspaceId,@Param("auditId")String auditId,@Param("actionCode")String actionCode,@Param("runnerId")String runnerId,@Param("actorType")String actorType,@Param("actorId")String actorId,@Param("correlationId")String correlationId,@Param("details")String details,@Param("now")LocalDateTime now);
}
