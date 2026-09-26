package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public class AdenTaskPersistenceRow {
    private String workspaceId;
    private String taskId;
    private String taskType;
    private String requiredCapability;
    private String taskState;
    private String correlationId;
    private String title;
    private String inputJson;
    private String errorCode;
    private long version;
    private long createdByRuoYiUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String value) { workspaceId = value; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { taskId = value; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String value) { taskType = value; }
    public String getRequiredCapability() { return requiredCapability; }
    public void setRequiredCapability(String value) { requiredCapability = value; }
    public String getTaskState() { return taskState; }
    public void setTaskState(String value) { taskState = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { correlationId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String value) { inputJson = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public long getCreatedByRuoYiUserId() { return createdByRuoYiUserId; }
    public void setCreatedByRuoYiUserId(long value) { createdByRuoYiUserId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
