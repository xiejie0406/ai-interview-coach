package com.ruoyi.aden.infrastructure.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.application.runner.AdenRunnerLedgerRepository;
import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenCredentialStatus;
import com.ruoyi.aden.domain.runner.AdenRunnerCredential;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenRunnerSession;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerCredentialRow;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerRow;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public final class MyBatisAdenRunnerLedgerRepository implements AdenRunnerLedgerRepository {
    private final AdenRunnerMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisAdenRunnerLedgerRepository(AdenRunnerMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper; this.objectMapper = objectMapper;
    }

    @Override public long lockWorkspaceEventSequence(AdenWorkspaceId workspaceId) {
        Long value=mapper.lockWorkspace(workspaceId.value()); if(value==null) throw new IllegalStateException("Workspace 不存在或不可用"); return value;
    }
    @Override public void insertRunner(RunnerFact r) { one(mapper.insertRunner(r.workspaceId().value(),r.runnerId().value(),r.displayName(),json(r.capabilities()),r.currentCredentialEpoch(),r.createdByUserId(),dt(r.createdAt())),"insert runner"); }
    @Override public void insertCredential(AdenRunnerCredential c) { one(mapper.insertCredential(c.workspaceId().value(),c.id().value(),c.runnerId().value(),c.epoch(),c.keyedDigest(),c.pepperKeyId(),dt(c.issuedAt()),dt(c.expiresAt())),"insert credential"); }
    @Override public Optional<RunnerFact> findRunnerForUpdate(AdenWorkspaceId w,AdenRunnerId r){return Optional.ofNullable(mapper.selectRunnerForUpdate(w.value(),r.value())).map(this::runner);}
    @Override public Optional<AdenRunnerCredential> findCredentialForUpdate(AdenWorkspaceId w,AdenCredentialId c){return Optional.ofNullable(mapper.selectCredentialForUpdate(w.value(),c.value())).map(this::credential);}
    @Override public void advanceRunnerForSession(RunnerFact p,long e,Instant n){one(mapper.advanceRunnerSession(p.workspaceId().value(),p.runnerId().value(),p.currentSessionEpoch(),p.version(),e,dt(n)),"advance runner session");}
    @Override public void supersedeActiveSessions(AdenWorkspaceId w,AdenRunnerId r,Instant n){mapper.supersedeSessions(w.value(),r.value(),dt(n));}
    @Override public void insertSession(AdenRunnerSession s,String d,String k,String m,Instant n){one(mapper.insertSession(s.workspaceId().value(),s.id().value(),s.runnerId().value(),s.credentialId().value(),s.epoch(),d,k,s.capacity(),m,dt(s.heartbeatAt()),dt(s.expiresAt())),"insert session");}
    @Override public void revokeRunner(RunnerFact p,Instant n){one(mapper.revokeRunner(p.workspaceId().value(),p.runnerId().value(),p.version(),dt(n)),"revoke runner");}
    @Override public void revokeActiveCredentials(AdenWorkspaceId w,AdenRunnerId r,Instant n){mapper.revokeCredentials(w.value(),r.value(),dt(n));}
    @Override public void revokeActiveSessions(AdenWorkspaceId w,AdenRunnerId r,Instant n){mapper.revokeSessions(w.value(),r.value(),dt(n));}
    @Override public void advanceWorkspaceEventSequence(AdenWorkspaceId w,long e,long x,Instant n){one(mapper.advanceWorkspace(w.value(),e,x,dt(n)),"advance workspace");}
    @Override public void appendRunnerEvent(RunnerEvent e){one(mapper.insertEvent(e.workspaceId().value(),e.eventId(),e.sequence(),e.runnerId().value(),e.aggregateVersion(),e.eventType(),e.payloadJson(),e.correlationId(),e.actorType(),e.actorId(),dt(e.occurredAt())),"insert event");}
    @Override public void insertOutbox(AdenWorkspaceId w,String o,String e,Instant n){one(mapper.insertOutbox(w.value(),o,e,dt(n)),"insert outbox");}
    @Override public void insertAudit(RunnerAudit a){one(mapper.insertAudit(a.workspaceId().value(),a.auditId(),a.actionCode(),a.runnerId().value(),a.actorType(),a.actorId(),a.correlationId(),a.detailsJson(),dt(a.occurredAt())),"insert audit");}

    private RunnerFact runner(AdenRunnerRow r){return new RunnerFact(new AdenWorkspaceId(r.getWorkspaceId()),new AdenRunnerId(r.getRunnerId()),r.getRunnerName(),r.getRunnerPresence(),capabilities(r.getCapabilitiesJson()),r.getCurrentSessionEpoch(),r.getCurrentCredentialEpoch(),r.getVersion(),r.getCreatedByRuoYiUserId(),AdenUtcDateTimeCodec.fromDatabase(r.getCreatedAt()),AdenUtcDateTimeCodec.fromDatabase(r.getUpdatedAt()));}
    private AdenRunnerCredential credential(AdenRunnerCredentialRow r){return new AdenRunnerCredential(new AdenWorkspaceId(r.getWorkspaceId()),new AdenCredentialId(r.getCredentialId()),new AdenRunnerId(r.getRunnerId()),r.getCredentialEpoch(),r.getCredentialKeyedDigest(),r.getPepperKeyId(),AdenCredentialStatus.valueOf(r.getCredentialStatus()),AdenUtcDateTimeCodec.fromDatabase(r.getIssuedAt()),AdenUtcDateTimeCodec.fromDatabase(r.getExpiresAt()));}
    private Set<AdenCapabilityCode> capabilities(String value){try{return objectMapper.readValue(value,new TypeReference<Set<AdenCapabilityCode>>(){});}catch(Exception e){throw new IllegalStateException("Runner capabilities 无法解析",e);}}
    private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Runner JSON 无法编码",e);}}
    private static java.time.LocalDateTime dt(Instant value){return AdenUtcDateTimeCodec.toDatabase(value);}
    private static void one(int count,String operation){if(count!=1)throw new IllegalStateException("Runner " + operation + " 竞争失败");}
}
