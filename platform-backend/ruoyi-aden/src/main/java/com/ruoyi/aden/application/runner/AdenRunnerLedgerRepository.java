package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenRunnerCredential;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenRunnerSession;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface AdenRunnerLedgerRepository {
    long lockWorkspaceEventSequence(AdenWorkspaceId workspaceId);
    void insertRunner(RunnerFact runner);
    void insertCredential(AdenRunnerCredential credential);
    Optional<RunnerFact> findRunnerForUpdate(AdenWorkspaceId workspaceId, AdenRunnerId runnerId);
    Optional<AdenRunnerCredential> findCredentialForUpdate(AdenWorkspaceId workspaceId, AdenCredentialId credentialId);
    void advanceRunnerForSession(RunnerFact previous, long nextSessionEpoch, Instant now);
    void supersedeActiveSessions(AdenWorkspaceId workspaceId, AdenRunnerId runnerId, Instant now);
    void insertSession(AdenRunnerSession session, String keyedDigest, String pepperKeyId,
                       String metadataJson, Instant now);
    void revokeRunner(RunnerFact previous, Instant now);
    void revokeActiveCredentials(AdenWorkspaceId workspaceId, AdenRunnerId runnerId, Instant now);
    void revokeActiveSessions(AdenWorkspaceId workspaceId, AdenRunnerId runnerId, Instant now);
    void advanceWorkspaceEventSequence(AdenWorkspaceId workspaceId, long expected, long next, Instant now);
    void appendRunnerEvent(RunnerEvent event);
    void insertOutbox(AdenWorkspaceId workspaceId, String outboxId, String eventId, Instant now);
    void insertAudit(RunnerAudit audit);

    record RunnerFact(AdenWorkspaceId workspaceId, AdenRunnerId runnerId, String displayName,
                      String presence, Set<AdenCapabilityCode> capabilities,
                      long currentSessionEpoch, long currentCredentialEpoch,
                      long version, long createdByUserId, Instant createdAt, Instant updatedAt) { }

    record RunnerEvent(AdenWorkspaceId workspaceId, String eventId, long sequence,
                       AdenRunnerId runnerId, long aggregateVersion, String eventType,
                       String payloadJson, String correlationId, String actorType,
                       String actorId, Instant occurredAt) { }

    record RunnerAudit(AdenWorkspaceId workspaceId, String auditId, String actionCode,
                       AdenRunnerId runnerId, String actorType, String actorId,
                       String correlationId, String detailsJson, Instant occurredAt) { }
}
