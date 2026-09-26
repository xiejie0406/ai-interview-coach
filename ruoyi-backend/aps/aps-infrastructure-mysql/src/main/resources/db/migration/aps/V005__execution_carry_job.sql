-- IMP09-D：新版候选通过 carry_run_id 引用既有 M25 run；不复制实际运行，也不改写 M26。
ALTER TABLE aps_plan_job
    ADD COLUMN carry_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER compatibility_key,
    DROP CHECK ck_plan_job_type,
    ADD CONSTRAINT ck_plan_job_type CHECK (job_type IN ('NORMAL', 'SPLIT', 'SHARED_BATCH', 'CARRY')),
    ADD CONSTRAINT uk_plan_job_carry_run UNIQUE (plan_version_id, carry_run_id),
    ADD CONSTRAINT fk_plan_job_carry_run FOREIGN KEY (carry_run_id)
        REFERENCES aps_execution_run (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT ck_plan_job_carry CHECK (
        (job_type = 'CARRY' AND carry_run_id IS NOT NULL)
        OR (job_type <> 'CARRY' AND carry_run_id IS NULL)
    );
