package com.ruoyi.aden.infrastructure.persistence.mapper;

public final class AdenTaskStepRow {
    private String workspaceId,stepId,taskId,stepState;
    private int attemptNo; private long version;
    public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;}
    public String getStepId(){return stepId;} public void setStepId(String v){stepId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getStepState(){return stepState;} public void setStepState(String v){stepState=v;}
    public int getAttemptNo(){return attemptNo;} public void setAttemptNo(int v){attemptNo=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
}
