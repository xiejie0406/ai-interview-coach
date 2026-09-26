package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public final class AdenRunnerRow {
    private String workspaceId; private String runnerId; private String runnerName;
    private String runnerPresence; private String capabilitiesJson;
    private long currentSessionEpoch; private long currentCredentialEpoch; private long version;
    private long createdByRuoYiUserId; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
    public String getRunnerId(){return runnerId;} public void setRunnerId(String v){runnerId=v;}
    public String getRunnerName(){return runnerName;} public void setRunnerName(String v){runnerName=v;}
    public String getRunnerPresence(){return runnerPresence;} public void setRunnerPresence(String v){runnerPresence=v;}
    public String getCapabilitiesJson(){return capabilitiesJson;} public void setCapabilitiesJson(String v){capabilitiesJson=v;}
    public long getCurrentSessionEpoch(){return currentSessionEpoch;} public void setCurrentSessionEpoch(long v){currentSessionEpoch=v;}
    public long getCurrentCredentialEpoch(){return currentCredentialEpoch;} public void setCurrentCredentialEpoch(long v){currentCredentialEpoch=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
    public long getCreatedByRuoYiUserId(){return createdByRuoYiUserId;} public void setCreatedByRuoYiUserId(long v){createdByRuoYiUserId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
