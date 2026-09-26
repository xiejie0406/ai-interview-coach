package com.ruoyi.aden.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdenProjectionMapper {
    Long selectWorkspaceWatermark(@Param("workspaceId") String workspaceId);

    List<TaskRow> selectTasks(@Param("workspaceId") String workspaceId,
                              @Param("capability") String capability,
                              @Param("beforeUpdatedAt") LocalDateTime beforeUpdatedAt,
                              @Param("beforeTaskId") String beforeTaskId,
                              @Param("limit") int limit);

    TaskRow selectTask(@Param("workspaceId") String workspaceId, @Param("taskId") String taskId);

    List<StepRow> selectSteps(@Param("workspaceId") String workspaceId, @Param("taskId") String taskId);

    List<RunnerRow> selectRunners(@Param("workspaceId") String workspaceId, @Param("limit") int limit);

    List<AuditRow> selectAudit(@Param("workspaceId") String workspaceId,
                               @Param("beforeOccurredAt") LocalDateTime beforeOccurredAt,
                               @Param("beforeAuditId") String beforeAuditId,
                               @Param("limit") int limit);

    List<EventRow> selectEvents(@Param("workspaceId") String workspaceId,
                                @Param("afterSequence") long afterSequence,
                                @Param("limit") int limit);

    Long selectOldestEventSequence(@Param("workspaceId") String workspaceId);

    class TaskRow {
        private String taskId, workspaceId, taskType, capabilityCode, title, state, reasonCode, correlationId;
        private long version;
        private LocalDateTime createdAt, updatedAt;
        public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
        public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
        public String getTaskType(){return taskType;} public void setTaskType(String v){taskType=v;}
        public String getCapabilityCode(){return capabilityCode;} public void setCapabilityCode(String v){capabilityCode=v;}
        public String getTitle(){return title;} public void setTitle(String v){title=v;}
        public String getState(){return state;} public void setState(String v){state=v;}
        public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;}
        public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
        public long getVersion(){return version;} public void setVersion(long v){version=v;}
        public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
        public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
    }

    class StepRow {
        private String stepId, taskId, state;
        private int ordinal, attemptNo, progressPercent;
        private long version;
        public String getStepId(){return stepId;} public void setStepId(String v){stepId=v;}
        public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
        public String getState(){return state;} public void setState(String v){state=v;}
        public int getOrdinal(){return ordinal;} public void setOrdinal(int v){ordinal=v;}
        public int getAttemptNo(){return attemptNo;} public void setAttemptNo(int v){attemptNo=v;}
        public int getProgressPercent(){return progressPercent;} public void setProgressPercent(int v){progressPercent=v;}
        public long getVersion(){return version;} public void setVersion(long v){version=v;}
    }

    class RunnerRow {
        private String runnerId, displayName, presence, capabilitiesJson;
        private long currentSessionEpoch;
        private LocalDateTime lastSeenAt;
        public String getRunnerId(){return runnerId;} public void setRunnerId(String v){runnerId=v;}
        public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
        public String getPresence(){return presence;} public void setPresence(String v){presence=v;}
        public String getCapabilitiesJson(){return capabilitiesJson;} public void setCapabilitiesJson(String v){capabilitiesJson=v;}
        public long getCurrentSessionEpoch(){return currentSessionEpoch;} public void setCurrentSessionEpoch(long v){currentSessionEpoch=v;}
        public LocalDateTime getLastSeenAt(){return lastSeenAt;} public void setLastSeenAt(LocalDateTime v){lastSeenAt=v;}
    }

    class AuditRow {
        private String auditEventId, workspaceId, actionCode, outcome, actorType, actorId, correlationId;
        private LocalDateTime occurredAt;
        public String getAuditEventId(){return auditEventId;} public void setAuditEventId(String v){auditEventId=v;}
        public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
        public String getActionCode(){return actionCode;} public void setActionCode(String v){actionCode=v;}
        public String getOutcome(){return outcome;} public void setOutcome(String v){outcome=v;}
        public String getActorType(){return actorType;} public void setActorType(String v){actorType=v;}
        public String getActorId(){return actorId;} public void setActorId(String v){actorId=v;}
        public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
        public LocalDateTime getOccurredAt(){return occurredAt;} public void setOccurredAt(LocalDateTime v){occurredAt=v;}
    }

    class EventRow {
        private String eventId, workspaceId, aggregateType, aggregateId, eventType, payloadJson, correlationId;
        private long sequence, aggregateVersion;
        private LocalDateTime occurredAt;
        public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;}
        public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
        public String getAggregateType(){return aggregateType;} public void setAggregateType(String v){aggregateType=v;}
        public String getAggregateId(){return aggregateId;} public void setAggregateId(String v){aggregateId=v;}
        public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
        public String getPayloadJson(){return payloadJson;} public void setPayloadJson(String v){payloadJson=v;}
        public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
        public long getSequence(){return sequence;} public void setSequence(long v){sequence=v;}
        public long getAggregateVersion(){return aggregateVersion;} public void setAggregateVersion(long v){aggregateVersion=v;}
        public LocalDateTime getOccurredAt(){return occurredAt;} public void setOccurredAt(LocalDateTime v){occurredAt=v;}
    }
}
