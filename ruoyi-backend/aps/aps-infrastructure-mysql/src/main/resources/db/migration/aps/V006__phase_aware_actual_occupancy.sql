-- IMP09-D：M26 保留原计划分配身份。换人/换机只改变实际 resource_id，
-- source_plan_allocation_id 继续指向产生该占用的 M22 席位，供恢复、阶段推进和重排使用。
ALTER TABLE aps_actual_occupancy
    ADD COLUMN source_plan_allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER plan_segment_id,
    ADD CONSTRAINT fk_actual_occupancy_source_allocation
        FOREIGN KEY (plan_segment_id, source_plan_allocation_id)
        REFERENCES aps_plan_allocation (plan_segment_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD KEY idx_actual_occupancy_source_allocation
        (plan_segment_id, source_plan_allocation_id);

-- 可唯一匹配的历史计划占用安全回填；历史换资源记录没有可靠来源时保持 NULL，
-- 应用层会把这类事实降级为 UNKNOWN，而不是猜测席位。
UPDATE aps_actual_occupancy o
JOIN aps_plan_allocation a
  ON a.plan_segment_id = o.plan_segment_id
 AND a.resource_id = o.resource_id
SET o.source_plan_allocation_id = a.id
WHERE o.plan_segment_id IS NOT NULL
  AND o.source_plan_allocation_id IS NULL;
