package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public final class AdenOutboxRow {
    private String workspaceId;
    private String outboxId;
    private String eventId;
    private long eventSequence;
    private String eventType;
    private String payloadJson;
    private String correlationId;
    private String consumer;
    private String outboxState;
    private int attempts;
    private String claimToken;
    private LocalDateTime claimedUntil;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String value) { workspaceId = value; }
    public String getOutboxId() { return outboxId; }
    public void setOutboxId(String value) { outboxId = value; }
    public String getEventId() { return eventId; }
    public void setEventId(String value) { eventId = value; }
    public long getEventSequence() { return eventSequence; }
    public void setEventSequence(long value) { eventSequence = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { payloadJson = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { correlationId = value; }
    public String getConsumer() { return consumer; }
    public void setConsumer(String value) { consumer = value; }
    public String getOutboxState() { return outboxState; }
    public void setOutboxState(String value) { outboxState = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String value) { claimToken = value; }
    public LocalDateTime getClaimedUntil() { return claimedUntil; }
    public void setClaimedUntil(LocalDateTime value) { claimedUntil = value; }
}
