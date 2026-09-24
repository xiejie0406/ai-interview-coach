package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.domain.workspace.AdenWorkspace;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceForbiddenAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMemberStatus;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRole;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceStatus;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenWorkspaceMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenWorkspaceRow;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisAdenWorkspaceRepository implements AdenWorkspaceRepository {
    private final AdenWorkspaceMapper mapper;

    public MyBatisAdenWorkspaceRepository(AdenWorkspaceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<AdenWorkspaceMembership> findActiveByUserId(long ruoYiUserId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit 必须在 1..100 范围内");
        return mapper.selectActiveByUserId(ruoYiUserId, limit).stream().map(this::toMembership).toList();
    }

    @Override
    public Optional<AdenWorkspaceMembership> findActiveMembership(AdenWorkspaceId workspaceId, long ruoYiUserId) {
        return Optional.ofNullable(mapper.selectActiveMembership(workspaceId.value(), ruoYiUserId))
                .map(this::toMembership);
    }

    @Override
    public void insertWorkspace(AdenWorkspace workspace) {
        int rows = mapper.insertWorkspace(
                workspace.id().value(), workspace.displayName(), workspace.status().name(), workspace.version(),
                workspace.createdByRuoYiUserId(), AdenUtcDateTimeCodec.toDatabase(workspace.createdAt()),
                AdenUtcDateTimeCodec.toDatabase(workspace.updatedAt()));
        requireOne(rows, "workspace");
    }

    @Override
    public void insertInitialOwner(AdenWorkspaceMembership membership) {
        int rows = mapper.insertMember(
                membership.workspace().id().value(), membership.ruoYiUserId(), membership.role().name(),
                membership.memberStatus().name(), AdenUtcDateTimeCodec.toDatabase(membership.membershipCreatedAt()),
                AdenUtcDateTimeCodec.toDatabase(membership.membershipCreatedAt()));
        requireOne(rows, "workspace member");
    }

    @Override
    public void insertAudit(AdenWorkspaceAudit audit) {
        int rows = mapper.insertAudit(
                audit.workspaceId().value(), audit.auditEventId(), audit.actionCode(), audit.resourceId(),
                Long.toString(audit.actorUserId()), audit.correlationId(),
                AdenUtcDateTimeCodec.toDatabase(audit.occurredAt()),
                AdenUtcDateTimeCodec.toDatabase(audit.occurredAt()));
        requireOne(rows, "audit event");
    }

    @Override
    public void insertWorkspaceForbiddenAudit(AdenWorkspaceForbiddenAudit audit) {
        int rows = mapper.insertWorkspaceForbiddenAudit(
                audit.auditEventId(), audit.requestedWorkspaceId().value(),
                Long.toString(audit.actorUserId()), audit.correlationId(),
                AdenUtcDateTimeCodec.toDatabase(audit.occurredAt()),
                AdenUtcDateTimeCodec.toDatabase(audit.occurredAt()));
        requireOne(rows, "workspace forbidden audit event");
    }

    private AdenWorkspaceMembership toMembership(AdenWorkspaceRow row) {
        AdenWorkspace workspace = new AdenWorkspace(
                new AdenWorkspaceId(row.getWorkspaceId()),
                row.getWorkspaceName(),
                AdenWorkspaceStatus.valueOf(row.getWorkspaceStatus()),
                row.getVersion(),
                row.getCreatedByRuoYiUserId(),
                AdenUtcDateTimeCodec.fromDatabase(row.getCreatedAt()),
                AdenUtcDateTimeCodec.fromDatabase(row.getUpdatedAt()));
        return new AdenWorkspaceMembership(
                workspace,
                row.getMemberUserId(),
                AdenWorkspaceRole.valueOf(row.getWorkspaceRole()),
                AdenWorkspaceMemberStatus.valueOf(row.getMemberStatus()),
                AdenUtcDateTimeCodec.fromDatabase(row.getMembershipCreatedAt()));
    }

    private static void requireOne(int rows, String operation) {
        if (rows != 1) throw new IllegalStateException(operation + " 写入行数必须为 1，实际为 " + rows);
    }
}
