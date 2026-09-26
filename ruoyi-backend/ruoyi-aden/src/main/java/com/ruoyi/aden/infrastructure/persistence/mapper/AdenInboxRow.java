package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public class AdenInboxRow {
    private String workspaceId;
    private String inboxId;
    private String producerId;
    private String operation;
    private String idempotencyKey;
    private String requestHash;
    private String inboxState;
    private String responseJson;
    private LocalDateTime inProgressUntil;
    private LocalDateTime retentionUntil;
    private LocalDateTime createdAt;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String value) { workspaceId = value; }
    public String getInboxId() { return inboxId; }
    public void setInboxId(String value) { inboxId = value; }
    public String getProducerId() { return producerId; }
    public void setProducerId(String value) { producerId = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { operation = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash = value; }
    public String getInboxState() { return inboxState; }
    public void setInboxState(String value) { inboxState = value; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String value) { responseJson = value; }
    public LocalDateTime getInProgressUntil() { return inProgressUntil; }
    public void setInProgressUntil(LocalDateTime value) { inProgressUntil = value; }
    public LocalDateTime getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(LocalDateTime value) { retentionUntil = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
