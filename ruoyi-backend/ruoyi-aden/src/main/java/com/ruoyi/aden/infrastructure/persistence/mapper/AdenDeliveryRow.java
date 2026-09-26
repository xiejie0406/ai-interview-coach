package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public final class AdenDeliveryRow {
    private String workspaceId,deliveryId,taskId,stepId,deliveryState,requiredCapability,taskPackageJson,taskPackageHash,ownerSessionId,runnerId;
    private int attemptNo,priority; private long ownerSessionEpoch,fenceToken,version,heartbeatSequence,latestReceiptSequence;
    private LocalDateTime leaseUntil,cancelRequestedAt,databaseNow;
    public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
    public String getDeliveryId(){return deliveryId;} public void setDeliveryId(String v){deliveryId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getStepId(){return stepId;} public void setStepId(String v){stepId=v;}
    public String getDeliveryState(){return deliveryState;} public void setDeliveryState(String v){deliveryState=v;}
    public String getRequiredCapability(){return requiredCapability;} public void setRequiredCapability(String v){requiredCapability=v;}
    public String getTaskPackageJson(){return taskPackageJson;} public void setTaskPackageJson(String v){taskPackageJson=v;}
    public String getTaskPackageHash(){return taskPackageHash;} public void setTaskPackageHash(String v){taskPackageHash=v;}
    public String getOwnerSessionId(){return ownerSessionId;} public void setOwnerSessionId(String v){ownerSessionId=v;}
    public String getRunnerId(){return runnerId;} public void setRunnerId(String v){runnerId=v;}
    public int getAttemptNo(){return attemptNo;} public void setAttemptNo(int v){attemptNo=v;}
    public int getPriority(){return priority;} public void setPriority(int v){priority=v;}
    public long getOwnerSessionEpoch(){return ownerSessionEpoch;} public void setOwnerSessionEpoch(long v){ownerSessionEpoch=v;}
    public long getFenceToken(){return fenceToken;} public void setFenceToken(long v){fenceToken=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
    public LocalDateTime getLeaseUntil(){return leaseUntil;} public void setLeaseUntil(LocalDateTime v){leaseUntil=v;}
    public long getHeartbeatSequence(){return heartbeatSequence;} public void setHeartbeatSequence(long v){heartbeatSequence=v;}
    public long getLatestReceiptSequence(){return latestReceiptSequence;} public void setLatestReceiptSequence(long v){latestReceiptSequence=v;}
    public LocalDateTime getCancelRequestedAt(){return cancelRequestedAt;} public void setCancelRequestedAt(LocalDateTime v){cancelRequestedAt=v;}
    public LocalDateTime getDatabaseNow(){return databaseNow;} public void setDatabaseNow(LocalDateTime v){databaseNow=v;}
}
