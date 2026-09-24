package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public final class AdenRunnerCredentialRow {
    private String workspaceId; private String credentialId; private String runnerId;
    private long credentialEpoch; private String credentialKeyedDigest; private String pepperKeyId;
    private String credentialStatus; private LocalDateTime issuedAt; private LocalDateTime expiresAt;
    public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
    public String getCredentialId(){return credentialId;} public void setCredentialId(String v){credentialId=v;}
    public String getRunnerId(){return runnerId;} public void setRunnerId(String v){runnerId=v;}
    public long getCredentialEpoch(){return credentialEpoch;} public void setCredentialEpoch(long v){credentialEpoch=v;}
    public String getCredentialKeyedDigest(){return credentialKeyedDigest;} public void setCredentialKeyedDigest(String v){credentialKeyedDigest=v;}
    public String getPepperKeyId(){return pepperKeyId;} public void setPepperKeyId(String v){pepperKeyId=v;}
    public String getCredentialStatus(){return credentialStatus;} public void setCredentialStatus(String v){credentialStatus=v;}
    public LocalDateTime getIssuedAt(){return issuedAt;} public void setIssuedAt(LocalDateTime v){issuedAt=v;}
    public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime v){expiresAt=v;}
}
