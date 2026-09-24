package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public final class AdenRunnerSessionRow {
    private String workspaceId; private String sessionId; private String runnerId; private String credentialId;
    private String sessionStatus; private long sessionEpoch; private String sessionKeyedDigest; private String pepperKeyId;
    private int capacity; private int inFlight; private long heartbeatSequence; private LocalDateTime heartbeatAt;
    private LocalDateTime expiresAt; private long runnerCurrentSessionEpoch; private long version;
    private LocalDateTime databaseNow;
    public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
    public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;}
    public String getRunnerId(){return runnerId;} public void setRunnerId(String v){runnerId=v;}
    public String getCredentialId(){return credentialId;} public void setCredentialId(String v){credentialId=v;}
    public String getSessionStatus(){return sessionStatus;} public void setSessionStatus(String v){sessionStatus=v;}
    public long getSessionEpoch(){return sessionEpoch;} public void setSessionEpoch(long v){sessionEpoch=v;}
    public String getSessionKeyedDigest(){return sessionKeyedDigest;} public void setSessionKeyedDigest(String v){sessionKeyedDigest=v;}
    public String getPepperKeyId(){return pepperKeyId;} public void setPepperKeyId(String v){pepperKeyId=v;}
    public int getCapacity(){return capacity;} public void setCapacity(int v){capacity=v;}
    public int getInFlight(){return inFlight;} public void setInFlight(int v){inFlight=v;}
    public long getHeartbeatSequence(){return heartbeatSequence;} public void setHeartbeatSequence(long v){heartbeatSequence=v;}
    public LocalDateTime getHeartbeatAt(){return heartbeatAt;} public void setHeartbeatAt(LocalDateTime v){heartbeatAt=v;}
    public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime v){expiresAt=v;}
    public long getRunnerCurrentSessionEpoch(){return runnerCurrentSessionEpoch;} public void setRunnerCurrentSessionEpoch(long v){runnerCurrentSessionEpoch=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
    public LocalDateTime getDatabaseNow(){return databaseNow;} public void setDatabaseNow(LocalDateTime v){databaseNow=v;}
}
