-- IMP-09：允许 REVERSE 精确反向一端为 NULL 的数量事件，例如 PRODUCE 的反向。
-- V001 保持不可变；服务层仍须锁定原事件并校验同批、同单位、同数量和精确相反方向。

ALTER TABLE aps_quantity_event
    DROP CHECK ck_quantity_event_shape;

ALTER TABLE aps_quantity_event
    ADD CONSTRAINT ck_quantity_event_shape CHECK (
        (event_type = 'PRODUCE' AND material_demand_id IS NULL AND execution_run_id IS NOT NULL AND production_report_id IS NOT NULL AND target_task_id IS NULL AND from_bucket IS NULL AND to_bucket = 'PENDING_QUALITY')
        OR (event_type = 'QUALITY_RELEASE' AND material_demand_id IS NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NULL AND from_bucket = 'PENDING_QUALITY' AND to_bucket = 'AVAILABLE')
        OR (event_type = 'RESERVE' AND material_demand_id IS NOT NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NOT NULL AND from_bucket = 'AVAILABLE' AND to_bucket = 'RESERVED')
        OR (event_type = 'UNRESERVE' AND material_demand_id IS NOT NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NOT NULL AND from_bucket = 'RESERVED' AND to_bucket = 'AVAILABLE')
        OR (event_type = 'CONSUME' AND material_demand_id IS NOT NULL AND execution_run_id IS NOT NULL AND production_report_id IS NULL AND target_task_id IS NOT NULL AND from_bucket IN ('AVAILABLE', 'RESERVED') AND to_bucket = 'CONSUMED')
        OR (event_type = 'TRANSFER' AND material_demand_id IS NOT NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NOT NULL AND from_bucket = 'AVAILABLE' AND to_bucket = 'TRANSFERRED')
        OR (event_type = 'SCRAP' AND material_demand_id IS NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NULL AND from_bucket IN ('PENDING_QUALITY', 'AVAILABLE', 'RESERVED') AND to_bucket = 'SCRAPPED')
        OR (event_type = 'ADJUST' AND material_demand_id IS NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NULL AND from_bucket IS NOT NULL AND to_bucket IS NOT NULL AND from_bucket <> to_bucket AND reason IS NOT NULL)
        OR (event_type = 'REVERSE' AND material_demand_id IS NULL AND execution_run_id IS NULL AND production_report_id IS NULL AND target_task_id IS NULL AND (from_bucket IS NOT NULL OR to_bucket IS NOT NULL) AND reason IS NOT NULL)
    );
