package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.runner.AdenRunnerAuthenticationPort;
import com.ruoyi.aden.application.runner.AdenRunnerCredentialPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerPepperProvider;
import com.ruoyi.aden.application.runner.AdenRunnerSessionPrincipal;
import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerCredentialRow;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerSessionRow;
import com.ruoyi.aden.infrastructure.security.AdenRunnerCredentialCodec;
import com.ruoyi.aden.infrastructure.security.AdenRunnerSessionCodec;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 认证查询只按 token 中公开 UUID 定位；Secret 从不进入 SQL、索引或日志。 */
public final class MyBatisAdenRunnerAuthenticationRepository implements AdenRunnerAuthenticationPort {
    private final AdenRunnerMapper mapper;
    private final AdenRunnerCredentialCodec credentials;
    private final AdenRunnerSessionCodec sessions;
    private final AdenRunnerPepperProvider peppers;

    public MyBatisAdenRunnerAuthenticationRepository(AdenRunnerMapper mapper,
                                                      AdenRunnerCredentialCodec credentials,
                                                      AdenRunnerSessionCodec sessions,
                                                      AdenRunnerPepperProvider peppers) {
        this.mapper=mapper; this.credentials=credentials; this.sessions=sessions; this.peppers=peppers;
    }

    @Override
    public Optional<AdenRunnerCredentialPrincipal> authenticateCredential(String token, Instant now) {
        Optional<AdenCredentialId> id=parseCredentialId(token); if(id.isEmpty()) return Optional.empty();
        List<AdenRunnerCredentialRow> rows=mapper.selectCredentialByPublicId(id.get().value());
        if(rows.size()!=1) return Optional.empty();
        AdenRunnerCredentialRow r=rows.get(0);
        if(!"ACTIVE".equals(r.getCredentialStatus()) || !now.isBefore(AdenUtcDateTimeCodec.fromDatabase(r.getExpiresAt()))) return Optional.empty();
        byte[] pepper;
        try{pepper=peppers.pepper(r.getPepperKeyId());}catch(RuntimeException missing){return Optional.empty();}
        if(!credentials.verify(token,id.get(),r.getCredentialKeyedDigest(),pepper)) return Optional.empty();
        return Optional.of(new AdenRunnerCredentialPrincipal(new AdenWorkspaceId(r.getWorkspaceId()),
                new AdenRunnerId(r.getRunnerId()),id.get(),r.getCredentialEpoch()));
    }

    @Override
    public Optional<AdenRunnerSessionPrincipal> authenticateSession(String token, Instant now) {
        Optional<AdenSessionId> id=parseSessionId(token); if(id.isEmpty()) return Optional.empty();
        List<AdenRunnerSessionRow> rows=mapper.selectSessionByPublicId(id.get().value());
        if(rows.size()!=1) return Optional.empty();
        AdenRunnerSessionRow r=rows.get(0);
        if(!"ACTIVE".equals(r.getSessionStatus()) || r.getSessionEpoch()!=r.getRunnerCurrentSessionEpoch()
                || !now.isBefore(AdenUtcDateTimeCodec.fromDatabase(r.getExpiresAt()))) return Optional.empty();
        byte[] pepper;
        try{pepper=peppers.pepper(r.getPepperKeyId());}catch(RuntimeException missing){return Optional.empty();}
        if(!sessions.verify(token,id.get(),r.getSessionKeyedDigest(),pepper)) return Optional.empty();
        return Optional.of(new AdenRunnerSessionPrincipal(new AdenWorkspaceId(r.getWorkspaceId()),
                new AdenRunnerId(r.getRunnerId()),id.get(),r.getSessionEpoch()));
    }

    private static Optional<AdenCredentialId> parseCredentialId(String token){try{return Optional.of(new AdenCredentialId(publicId(token)));}catch(RuntimeException e){return Optional.empty();}}
    private static Optional<AdenSessionId> parseSessionId(String token){try{return Optional.of(new AdenSessionId(publicId(token)));}catch(RuntimeException e){return Optional.empty();}}
    private static String publicId(String token){if(token==null)throw new IllegalArgumentException();int i=token.indexOf('.');if(i<1||i!=token.lastIndexOf('.'))throw new IllegalArgumentException();return token.substring(0,i);}
}
