package com.ruoyi.aden.application.projection;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Operator 读模型端口；全部查询必须显式携带 workspace 边界。 */
public interface AdenOperatorProjectionRepository {
    long workspaceWatermark(AdenWorkspaceId workspaceId);

    List<TaskView> listTasks(AdenWorkspaceId workspaceId, String capability,
                             Instant beforeUpdatedAt, String beforeTaskId, int limit);

    Optional<TaskView> findTask(AdenWorkspaceId workspaceId, String taskId);

    List<StepView> listSteps(AdenWorkspaceId workspaceId, String taskId);

    List<RunnerView> listRunners(AdenWorkspaceId workspaceId, int limit);

    List<AuditView> listAudit(AdenWorkspaceId workspaceId, Instant beforeOccurredAt,
                              String beforeAuditId, int limit);

    List<EventView> listEvents(AdenWorkspaceId workspaceId, long afterSequence, int limit);

    OptionalLongValue oldestEventSequence(AdenWorkspaceId workspaceId);

    record TaskView(String taskId, String workspaceId, String taskType, String capabilityCode,
                    String title, String state, long version, String reasonCode,
                    Instant createdAt, Instant updatedAt, String correlationId) { }

    record StepView(String stepId, String taskId, int ordinal, String state,
                    int attemptNo, long version, int progressPercent) { }

    record RunnerView(String runnerId, String displayName, String presence,
                      String capabilitiesJson, long currentSessionEpoch, Instant lastSeenAt) { }

    record AuditView(String auditEventId, String workspaceId, String actionCode, String outcome,
                     String actorType, String actorId, Instant occurredAt, String correlationId) { }

    record EventView(String eventId, String workspaceId, long sequence, String aggregateType,
                     String aggregateId, long aggregateVersion, String eventType,
                     String payloadJson, Instant occurredAt, String correlationId) { }

    record OptionalLongValue(boolean present, long value) {
        public static OptionalLongValue empty() { return new OptionalLongValue(false, 0); }
        public static OptionalLongValue of(long value) { return new OptionalLongValue(true, value); }
    }
}
