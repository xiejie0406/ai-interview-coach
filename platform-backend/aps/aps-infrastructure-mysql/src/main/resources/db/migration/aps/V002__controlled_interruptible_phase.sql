-- IMP-07：补齐受控可中断阶段的最小分段、恢复准备、资源连续性与暂停保留语义。
-- V001 已冻结；已有环境只允许通过此前进 migration 增量升级。

ALTER TABLE aps_operation_phase
    ADD COLUMN max_segments SMALLINT UNSIGNED NOT NULL DEFAULT 1 AFTER resource_hold_policy,
    ADD COLUMN min_segment_seconds INT UNSIGNED NOT NULL DEFAULT 0 AFTER max_segments,
    ADD COLUMN resume_setup_seconds INT UNSIGNED NOT NULL DEFAULT 0 AFTER min_segment_seconds,
    ADD COLUMN segment_resource_policy VARCHAR(20) NOT NULL DEFAULT 'SAME_RESOURCES' AFTER resume_setup_seconds,
    ADD CONSTRAINT ck_phase_segmentation CHECK (
        (max_segments = 1 AND min_segment_seconds = 0 AND resume_setup_seconds = 0)
        OR (max_segments BETWEEN 2 AND 100 AND min_segment_seconds > 0)
    ),
    ADD CONSTRAINT ck_phase_segment_resource CHECK (
        segment_resource_policy IN ('SAME_RESOURCES', 'RESELECT_ALLOWED')
    );

ALTER TABLE aps_resource_requirement
    ADD COLUMN hold_on_pause TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER optional_flag,
    ADD CONSTRAINT ck_requirement_hold_on_pause CHECK (hold_on_pause IN (0, 1));
