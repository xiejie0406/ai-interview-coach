package com.ruoyi.aden.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public class AdenWorkspaceRow {
    private String workspaceId;
    private String workspaceName;
    private String workspaceStatus;
    private long version;
    private long createdByRuoYiUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long memberUserId;
    private String workspaceRole;
    private String memberStatus;
    private LocalDateTime membershipCreatedAt;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String value) { this.workspaceId = value; }
    public String getWorkspaceName() { return workspaceName; }
    public void setWorkspaceName(String value) { this.workspaceName = value; }
    public String getWorkspaceStatus() { return workspaceStatus; }
    public void setWorkspaceStatus(String value) { this.workspaceStatus = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { this.version = value; }
    public long getCreatedByRuoYiUserId() { return createdByRuoYiUserId; }
    public void setCreatedByRuoYiUserId(long value) { this.createdByRuoYiUserId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public long getMemberUserId() { return memberUserId; }
    public void setMemberUserId(long value) { this.memberUserId = value; }
    public String getWorkspaceRole() { return workspaceRole; }
    public void setWorkspaceRole(String value) { this.workspaceRole = value; }
    public String getMemberStatus() { return memberStatus; }
    public void setMemberStatus(String value) { this.memberStatus = value; }
    public LocalDateTime getMembershipCreatedAt() { return membershipCreatedAt; }
    public void setMembershipCreatedAt(LocalDateTime value) { this.membershipCreatedAt = value; }
}
