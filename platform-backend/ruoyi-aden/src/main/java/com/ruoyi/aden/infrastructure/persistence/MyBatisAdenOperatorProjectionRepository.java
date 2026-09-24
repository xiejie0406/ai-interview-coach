package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.projection.AdenOperatorProjectionRepository;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenProjectionMapper;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class MyBatisAdenOperatorProjectionRepository implements AdenOperatorProjectionRepository {
    private final AdenProjectionMapper mapper;

    public MyBatisAdenOperatorProjectionRepository(AdenProjectionMapper mapper) { this.mapper = mapper; }

    @Override public long workspaceWatermark(AdenWorkspaceId workspaceId) {
        Long value = mapper.selectWorkspaceWatermark(workspaceId.value());
        if (value == null) throw new IllegalStateException("Workspace 水位不存在");
        return value;
    }
    @Override public List<TaskView> listTasks(AdenWorkspaceId w,String c,Instant at,String id,int n){
        return mapper.selectTasks(w.value(),c,local(at),id,n).stream().map(this::task).toList();
    }
    @Override public Optional<TaskView> findTask(AdenWorkspaceId w,String id){return Optional.ofNullable(mapper.selectTask(w.value(),id)).map(this::task);}
    @Override public List<StepView> listSteps(AdenWorkspaceId w,String id){return mapper.selectSteps(w.value(),id).stream().map(r->new StepView(r.getStepId(),r.getTaskId(),r.getOrdinal(),r.getState(),r.getAttemptNo(),r.getVersion(),r.getProgressPercent())).toList();}
    @Override public List<RunnerView> listRunners(AdenWorkspaceId w,int n){return mapper.selectRunners(w.value(),n).stream().map(r->new RunnerView(r.getRunnerId(),r.getDisplayName(),r.getPresence(),r.getCapabilitiesJson(),r.getCurrentSessionEpoch(),instant(r.getLastSeenAt()))).toList();}
    @Override public List<AuditView> listAudit(AdenWorkspaceId w,Instant at,String id,int n){return mapper.selectAudit(w.value(),local(at),id,n).stream().map(r->new AuditView(r.getAuditEventId(),r.getWorkspaceId(),r.getActionCode(),r.getOutcome(),r.getActorType(),r.getActorId(),instant(r.getOccurredAt()),r.getCorrelationId())).toList();}
    @Override public List<EventView> listEvents(AdenWorkspaceId w,long after,int n){return mapper.selectEvents(w.value(),after,n).stream().map(r->new EventView(r.getEventId(),r.getWorkspaceId(),r.getSequence(),r.getAggregateType(),r.getAggregateId(),r.getAggregateVersion(),r.getEventType(),r.getPayloadJson(),instant(r.getOccurredAt()),r.getCorrelationId())).toList();}
    @Override public OptionalLongValue oldestEventSequence(AdenWorkspaceId w){Long v=mapper.selectOldestEventSequence(w.value());return v==null?OptionalLongValue.empty():OptionalLongValue.of(v);}
    private TaskView task(AdenProjectionMapper.TaskRow r){return new TaskView(r.getTaskId(),r.getWorkspaceId(),r.getTaskType(),r.getCapabilityCode(),r.getTitle(),r.getState(),r.getVersion(),r.getReasonCode(),instant(r.getCreatedAt()),instant(r.getUpdatedAt()),r.getCorrelationId());}
    private static java.time.LocalDateTime local(Instant value){return value==null?null:AdenUtcDateTimeCodec.toDatabase(value);}
    private static Instant instant(java.time.LocalDateTime value){return value==null?null:AdenUtcDateTimeCodec.fromDatabase(value);}
}
