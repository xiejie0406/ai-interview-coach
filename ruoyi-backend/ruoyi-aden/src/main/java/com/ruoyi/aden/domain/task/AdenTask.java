package com.ruoyi.aden.domain.task;

import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/** 不可变 Task 聚合；每次合法迁移精确增加一个 aggregate version 并产生一个领域事件。 */
public record AdenTask(
        AdenWorkspaceId workspaceId,
        AdenTaskId id,
        AdenTaskType type,
        AdenCapabilityCode capability,
        String title,
        AdenTaskState state,
        AdenTaskVersion version,
        AdenCorrelationId correlationId,
        long createdByRuoYiUserId,
        Instant createdAt,
        Instant updatedAt) {

    public AdenTask {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(capability, "capability");
        title = normalizeTitle(title);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(correlationId, "correlationId");
        if (createdByRuoYiUserId <= 0) throw new IllegalArgumentException("createdByRuoYiUserId 必须为正数");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt 不得早于 createdAt");
        if (type == AdenTaskType.SYNTHETIC_CORE && capability != AdenCapabilityCode.CORE) {
            throw new IllegalArgumentException("SYNTHETIC_CORE 任务只能使用 CORE capability");
        }
        if (type != AdenTaskType.SYNTHETIC_CORE && capability != AdenCapabilityCode.COL) {
            throw new IllegalArgumentException("商品采集任务只能使用 COL capability");
        }
    }

    public AdenTaskTransition transition(AdenTaskActor actor, AdenTaskCommand command, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (occurredAt.isBefore(updatedAt)) throw new IllegalArgumentException("迁移时间不得倒退");
        AdenTaskState nextState = AdenTaskTransitions.apply(state, actor, command);
        AdenTaskVersion nextVersion = version.next();
        AdenTask nextTask = new AdenTask(
                workspaceId, id, type, capability, title, nextState, nextVersion,
                correlationId, createdByRuoYiUserId, createdAt, occurredAt);
        return new AdenTaskTransition(nextTask, new AdenTaskStateChanged(
                workspaceId, id, state, nextState, command, actor, nextVersion, correlationId, occurredAt));
    }

    /** 状态不变但受信任内部事实（如进度或重试安排）改变时推进聚合版本。 */
    public AdenTask advanceContentVersion(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (occurredAt.isBefore(updatedAt)) throw new IllegalArgumentException("聚合更新时间不得倒退");
        return new AdenTask(workspaceId, id, type, capability, title, state, version.next(),
                correlationId, createdByRuoYiUserId, createdAt, occurredAt);
    }

    public static String normalizeTitle(String value) {
        if (value == null) throw new IllegalArgumentException("title 不能为空");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 120
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("title 必须为 1..120 个非换行字符");
        }
        return normalized;
    }
}
