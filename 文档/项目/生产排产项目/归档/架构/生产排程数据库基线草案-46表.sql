-- 历史归档：生产排程标准闭环数据库基线草案（46 表）
-- 编号：DDL-APS-MYSQL-001；版本：2.0.0；状态：Review-only / Not executed
-- 对应设计：数据库设计.md（46 张表）
-- 目标方言：MySQL 8.0.16+ / InnoDB / utf8mb4
-- 使用边界：只允许在独立、空的 production-scheduling 数据库中评审和验证。
-- 本文件不是现有应用的 Flyway migration；不得直接用于生产，不包含 DROP/TRUNCATE/DELETE/业务 seed。
-- 时间：所有 DATETIME(3) 由应用按 UTC 写入，工厂 IANA 时区保存在 aps_factory.timezone。
-- 主键：应用生成 UUID 文本；RuoYi 用户和部门字段仅逻辑引用，不建立跨数据库外键。
-- 物理顺序：T09 在 T04 前创建以满足单位外键；其余编号仍按设计中的逻辑分组阅读。
-- 外键删除/更新动作未显式书写时采用 MySQL 默认 RESTRICT；严禁对历史事实做级联删除。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- T01 工厂
CREATE TABLE aps_factory (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ruoyi_dept_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_factory_code (factory_code),
    CONSTRAINT ck_factory_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T01 工厂';

-- T02 车间
CREATE TABLE aps_workshop (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workshop_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    ruoyi_dept_id BIGINT NULL,
    manager_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workshop_factory_id (factory_id, id),
    UNIQUE KEY uq_workshop_code (factory_id, workshop_code),
    CONSTRAINT fk_workshop_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT ck_workshop_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T02 车间';

-- T03 工作中心
CREATE TABLE aps_work_center (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workshop_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    center_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    center_kind VARCHAR(32) NOT NULL DEFAULT 'MIXED',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_center_factory_id (factory_id, id),
    UNIQUE KEY uq_center_code (factory_id, center_code),
    KEY ix_center_workshop (factory_id, workshop_id),
    CONSTRAINT fk_center_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_center_workshop FOREIGN KEY (factory_id, workshop_id) REFERENCES aps_workshop (factory_id, id),
    CONSTRAINT ck_center_kind CHECK (center_kind IN ('MANUAL', 'MACHINE', 'MIXED')),
    CONSTRAINT ck_center_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T03 工作中心';

-- T09 计量单位先建，供资源和物料引用；逻辑编号仍为 T09。
CREATE TABLE aps_uom (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uom_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    dimension VARCHAR(32) NOT NULL,
    quantum DECIMAL(18, 6) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_uom_factory_id (factory_id, id),
    UNIQUE KEY uq_uom_code (factory_id, uom_code),
    CONSTRAINT fk_uom_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT ck_uom_dimension CHECK (dimension IN ('COUNT', 'TIME', 'WEIGHT', 'VOLUME', 'CAPACITY')),
    CONSTRAINT ck_uom_quantum CHECK (quantum > 0),
    CONSTRAINT ck_uom_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T09 计量单位';

-- T04 统一资源与人员资料
CREATE TABLE aps_resource (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    home_workshop_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capacity_uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capacity_value DECIMAL(18, 6) NOT NULL DEFAULT 1,
    is_exclusive BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    employee_no VARCHAR(64) NULL,
    login_user_id BIGINT NULL,
    team_name VARCHAR(128) NULL,
    employment_start DATE NULL,
    employment_end DATE NULL,
    hr_reference VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_resource_factory_id (factory_id, id),
    UNIQUE KEY uq_resource_code (factory_id, resource_code),
    UNIQUE KEY uq_resource_employee (factory_id, employee_no),
    UNIQUE KEY uq_resource_login (factory_id, login_user_id),
    KEY ix_resource_workshop (factory_id, home_workshop_id),
    KEY ix_resource_uom (factory_id, capacity_uom_id),
    CONSTRAINT fk_resource_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_resource_workshop FOREIGN KEY (factory_id, home_workshop_id) REFERENCES aps_workshop (factory_id, id),
    CONSTRAINT fk_resource_uom FOREIGN KEY (factory_id, capacity_uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_resource_type CHECK (resource_type IN ('PERSON', 'MACHINE', 'STATION', 'TOOL', 'CAPACITY')),
    CONSTRAINT ck_resource_capacity CHECK (capacity_value > 0),
    CONSTRAINT ck_resource_person CHECK (
        (resource_type = 'PERSON' AND employee_no IS NOT NULL AND employment_start IS NOT NULL AND capacity_value = 1)
        OR (resource_type <> 'PERSON' AND employee_no IS NULL AND login_user_id IS NULL AND team_name IS NULL AND employment_start IS NULL AND employment_end IS NULL AND hr_reference IS NULL)
    ),
    CONSTRAINT ck_resource_exclusive CHECK (is_exclusive IN (0, 1)),
    CONSTRAINT ck_resource_dates CHECK (employment_end IS NULL OR employment_start IS NULL OR employment_end >= employment_start),
    CONSTRAINT ck_resource_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T04 统一资源与人员资料';

-- T05 技能字典
CREATE TABLE aps_skill (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    applies_to VARCHAR(32) NOT NULL DEFAULT 'PERSON',
    max_level SMALLINT NOT NULL DEFAULT 5,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_skill_factory_id (factory_id, id),
    UNIQUE KEY uq_skill_code (factory_id, skill_code),
    CONSTRAINT fk_skill_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT ck_skill_applies CHECK (applies_to IN ('PERSON', 'EQUIPMENT')),
    CONSTRAINT ck_skill_level CHECK (max_level > 0),
    CONSTRAINT ck_skill_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T05 技能字典';

-- T06 资源技能有效记录
CREATE TABLE aps_resource_skill (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    skill_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    level SMALLINT NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_to DATETIME(3) NULL,
    certification_ref VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_resource_skill_factory_id (factory_id, id),
    UNIQUE KEY uq_resource_skill_from (factory_id, resource_id, skill_id, valid_from),
    KEY ix_resource_skill_skill (factory_id, skill_id),
    CONSTRAINT fk_res_skill_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_res_skill_resource FOREIGN KEY (factory_id, resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT fk_res_skill_skill FOREIGN KEY (factory_id, skill_id) REFERENCES aps_skill (factory_id, id),
    CONSTRAINT ck_res_skill_level CHECK (level > 0),
    CONSTRAINT ck_res_skill_dates CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T06 资源技能有效记录';

-- T07 中心资格与借调
CREATE TABLE aps_resource_center (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_center_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_to DATETIME(3) NULL,
    assignment_kind VARCHAR(32) NOT NULL DEFAULT 'HOME',
    approved_by BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_resource_center_factory_id (factory_id, id),
    UNIQUE KEY uq_resource_center_from (factory_id, resource_id, work_center_id, valid_from),
    KEY ix_resource_center_center (factory_id, work_center_id),
    CONSTRAINT fk_res_center_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_res_center_resource FOREIGN KEY (factory_id, resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT fk_res_center_center FOREIGN KEY (factory_id, work_center_id) REFERENCES aps_work_center (factory_id, id),
    CONSTRAINT ck_res_center_kind CHECK (assignment_kind IN ('HOME', 'QUALIFIED', 'SECONDMENT')),
    CONSTRAINT ck_res_center_dates CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T07 中心资格与借调';

-- T08 资源可用窗口
CREATE TABLE aps_resource_availability (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    availability_type VARCHAR(32) NOT NULL DEFAULT 'WORK',
    capacity_value DECIMAL(18, 6) NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    approved_by BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    supersedes_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_availability_factory_id (factory_id, id),
    UNIQUE KEY uq_availability_supersedes (factory_id, supersedes_id),
    KEY ix_availability_resource_time (factory_id, resource_id, start_at, end_at),
    CONSTRAINT fk_avail_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_avail_resource FOREIGN KEY (factory_id, resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT fk_avail_supersedes FOREIGN KEY (factory_id, supersedes_id) REFERENCES aps_resource_availability (factory_id, id),
    CONSTRAINT ck_avail_time CHECK (end_at > start_at),
    CONSTRAINT ck_avail_capacity CHECK (
        (availability_type IN ('WORK', 'OVERTIME') AND capacity_value > 0)
        OR (availability_type IN ('LEAVE', 'TRAINING', 'MAINTENANCE', 'BREAK') AND capacity_value = 0)
    ),
    CONSTRAINT ck_avail_type CHECK (availability_type IN ('WORK', 'OVERTIME', 'LEAVE', 'TRAINING', 'MAINTENANCE', 'BREAK')),
    CONSTRAINT ck_avail_approval CHECK (approval_status IN ('DRAFT', 'APPROVED', 'CANCELLED')),
    CONSTRAINT ck_avail_approved_by CHECK (approval_status <> 'APPROVED' OR approved_by IS NOT NULL),
    CONSTRAINT ck_avail_supersedes_self CHECK (supersedes_id IS NULL OR supersedes_id <> id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T08 资源可用窗口';

-- T10 产品与物料版本
CREATE TABLE aps_item (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    revision_no VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    item_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCT',
    base_uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_item_factory_id (factory_id, id),
    UNIQUE KEY uq_item_revision (factory_id, item_code, revision_no),
    UNIQUE KEY uq_item_external (factory_id, source_system, external_id, revision_no),
    KEY ix_item_uom (factory_id, base_uom_id),
    CONSTRAINT fk_item_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_item_uom FOREIGN KEY (factory_id, base_uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_item_type CHECK (item_type IN ('PRODUCT', 'SEMI_FINISHED', 'RAW_MATERIAL')),
    CONSTRAINT ck_item_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T10 产品与物料版本';

-- T11 工序定义与参数版本
CREATE TABLE aps_operation_spec (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    spec_no INT NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'PROCESS',
    description TEXT NULL,
    mode VARCHAR(32) NOT NULL,
    output_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quality_policy VARCHAR(32) NOT NULL DEFAULT 'REPORT_RELEASE',
    compatibility_key VARCHAR(128) NULL,
    min_batch_qty DECIMAL(18, 6) NULL,
    max_batch_qty DECIMAL(18, 6) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approved_by BIGINT NULL,
    approved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_operation_spec_factory_id (factory_id, id),
    UNIQUE KEY uq_operation_spec_version (factory_id, operation_code, spec_no),
    KEY ix_operation_spec_item (factory_id, output_item_id),
    CONSTRAINT fk_op_spec_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_op_spec_item FOREIGN KEY (factory_id, output_item_id) REFERENCES aps_item (factory_id, id),
    CONSTRAINT ck_op_spec_no CHECK (spec_no > 0),
    CONSTRAINT ck_op_spec_category CHECK (category IN ('PROCESS', 'INSPECTION', 'TRANSFER', 'WAIT')),
    CONSTRAINT ck_op_spec_mode CHECK (mode IN ('MANUAL', 'MACHINE', 'AUTO', 'BATCH', 'WAIT')),
    CONSTRAINT ck_op_spec_quality CHECK (quality_policy IN ('REPORT_RELEASE', 'INSPECTION_REQUIRED')),
    CONSTRAINT ck_op_spec_batch CHECK (
        (mode = 'BATCH' AND compatibility_key IS NOT NULL AND max_batch_qty IS NOT NULL
            AND max_batch_qty > 0 AND (min_batch_qty IS NULL OR min_batch_qty > 0)
            AND (min_batch_qty IS NULL OR max_batch_qty >= min_batch_qty))
        OR
        (mode <> 'BATCH' AND compatibility_key IS NULL AND min_batch_qty IS NULL AND max_batch_qty IS NULL)
    ),
    CONSTRAINT ck_op_spec_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_op_spec_published CHECK (status <> 'PUBLISHED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T11 工序定义与参数版本';

-- T12 工艺阶段与默认标准工时
CREATE TABLE aps_operation_phase (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phase_no INT NOT NULL,
    phase_kind VARCHAR(32) NOT NULL,
    duration_mode VARCHAR(32) NOT NULL DEFAULT 'FIXED',
    interruptible BOOLEAN NOT NULL DEFAULT FALSE,
    resume_setup_seconds BIGINT NOT NULL DEFAULT 0,
    hold_on_pause BOOLEAN NOT NULL DEFAULT FALSE,
    releases_output BOOLEAN NOT NULL DEFAULT FALSE,
    fixed_seconds BIGINT NOT NULL,
    seconds_per_unit DECIMAL(18, 9) NOT NULL,
    output_uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    value_source VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(256) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_phase_factory_id (factory_id, id),
    UNIQUE KEY uq_phase_no (factory_id, operation_spec_id, phase_no),
    KEY ix_phase_uom (factory_id, output_uom_id),
    CONSTRAINT fk_phase_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_phase_spec FOREIGN KEY (factory_id, operation_spec_id) REFERENCES aps_operation_spec (factory_id, id),
    CONSTRAINT fk_phase_uom FOREIGN KEY (factory_id, output_uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_phase_no CHECK (phase_no > 0),
    CONSTRAINT ck_phase_kind CHECK (phase_kind IN ('SETUP', 'RUN', 'UNLOAD', 'WAIT', 'TRANSFER', 'RECOVERY_SETUP')),
    CONSTRAINT ck_phase_mode CHECK (duration_mode IN ('FIXED', 'PER_UNIT')),
    CONSTRAINT ck_phase_time CHECK (resume_setup_seconds >= 0 AND fixed_seconds >= 0 AND seconds_per_unit >= 0),
    CONSTRAINT ck_phase_rate CHECK ((duration_mode = 'FIXED' AND seconds_per_unit = 0) OR (duration_mode = 'PER_UNIT' AND seconds_per_unit > 0)),
    CONSTRAINT ck_phase_flags CHECK (interruptible IN (0, 1) AND hold_on_pause IN (0, 1) AND releases_output IN (0, 1)),
    CONSTRAINT ck_phase_source CHECK (value_source IN ('STANDARD', 'OBSERVED_APPROVED', 'ESTIMATE_APPROVED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T12 工艺阶段与默认标准工时';

-- T13 阶段资源角色与席位
CREATE TABLE aps_resource_requirement (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phase_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    work_center_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fixed_resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    seat_count INT NOT NULL DEFAULT 1,
    demand_per_seat DECIMAL(18, 6) NOT NULL DEFAULT 1,
    demand_uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    continuity_key VARCHAR(64) NULL,
    distinct_group VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_requirement_factory_id (factory_id, id),
    UNIQUE KEY uq_requirement_role (factory_id, phase_id, role_code),
    KEY ix_requirement_center (factory_id, work_center_id),
    KEY ix_requirement_resource (factory_id, fixed_resource_id),
    KEY ix_requirement_uom (factory_id, demand_uom_id),
    CONSTRAINT fk_requirement_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_requirement_phase FOREIGN KEY (factory_id, phase_id) REFERENCES aps_operation_phase (factory_id, id),
    CONSTRAINT fk_requirement_center FOREIGN KEY (factory_id, work_center_id) REFERENCES aps_work_center (factory_id, id),
    CONSTRAINT fk_requirement_resource FOREIGN KEY (factory_id, fixed_resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT fk_requirement_uom FOREIGN KEY (factory_id, demand_uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_requirement_type CHECK (resource_type IN ('PERSON', 'MACHINE', 'STATION', 'TOOL', 'CAPACITY')),
    CONSTRAINT ck_requirement_seats CHECK (seat_count > 0 AND demand_per_seat > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T13 阶段资源角色与席位';

-- T14 角色技能要求
CREATE TABLE aps_requirement_skill (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requirement_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    skill_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    minimum_level SMALLINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_req_skill_factory_id (factory_id, id),
    UNIQUE KEY uq_req_skill_pair (factory_id, requirement_id, skill_id),
    KEY ix_req_skill_skill (factory_id, skill_id),
    CONSTRAINT fk_req_skill_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_req_skill_req FOREIGN KEY (factory_id, requirement_id) REFERENCES aps_resource_requirement (factory_id, id),
    CONSTRAINT fk_req_skill_skill FOREIGN KEY (factory_id, skill_id) REFERENCES aps_skill (factory_id, id),
    CONSTRAINT ck_req_skill_level CHECK (minimum_level > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T14 角色技能要求';

-- T15 产品工艺路线版本
CREATE TABLE aps_route_version (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_code VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    approved_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_route_factory_id (factory_id, id),
    UNIQUE KEY uq_route_item_id (factory_id, item_id, id),
    UNIQUE KEY uq_route_version (factory_id, route_code, version_no),
    CONSTRAINT fk_route_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_route_item FOREIGN KEY (factory_id, item_id) REFERENCES aps_item (factory_id, id),
    CONSTRAINT ck_route_version CHECK (version_no > 0),
    CONSTRAINT ck_route_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_route_published CHECK (status <> 'PUBLISHED' OR (published_at IS NOT NULL AND approved_by IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T15 产品工艺路线版本';

-- T16 路线节点
CREATE TABLE aps_route_node (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_code VARCHAR(64) NOT NULL,
    operation_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity_factor DECIMAL(18, 9) NOT NULL DEFAULT 1,
    required_for_completion BOOLEAN NOT NULL DEFAULT TRUE,
    is_delivery_output BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_route_node_factory_id (factory_id, id),
    UNIQUE KEY uq_route_node_route_id (factory_id, route_version_id, id),
    UNIQUE KEY uq_route_node_code (factory_id, route_version_id, node_code),
    KEY ix_route_node_spec (factory_id, operation_spec_id),
    CONSTRAINT fk_route_node_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_route_node_route FOREIGN KEY (factory_id, route_version_id) REFERENCES aps_route_version (factory_id, id),
    CONSTRAINT fk_route_node_spec FOREIGN KEY (factory_id, operation_spec_id) REFERENCES aps_operation_spec (factory_id, id),
    CONSTRAINT ck_route_node_factor CHECK (quantity_factor > 0),
    CONSTRAINT ck_route_node_flags CHECK (required_for_completion IN (0, 1) AND is_delivery_output IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T16 路线节点';

-- T17 路线模板关系
CREATE TABLE aps_route_edge (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_node_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    to_node_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'FINISH',
    threshold_mode VARCHAR(32) NULL,
    threshold_qty DECIMAL(18, 6) NULL,
    threshold_ratio DECIMAL(18, 9) NULL,
    usage_ratio DECIMAL(18, 9) NULL,
    transfer_qty DECIMAL(18, 6) NULL,
    lag_seconds BIGINT NOT NULL DEFAULT 0,
    lag_basis VARCHAR(32) NOT NULL DEFAULT 'ELAPSED',
    lag_calendar_resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_route_edge_factory_id (factory_id, id),
    UNIQUE KEY uq_route_edge_relation (factory_id, route_version_id, from_node_id, to_node_id, relation_type),
    KEY ix_route_edge_to (factory_id, route_version_id, to_node_id),
    KEY ix_route_edge_calendar (factory_id, lag_calendar_resource_id),
    CONSTRAINT fk_route_edge_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_route_edge_route FOREIGN KEY (factory_id, route_version_id) REFERENCES aps_route_version (factory_id, id),
    CONSTRAINT fk_route_edge_from FOREIGN KEY (factory_id, route_version_id, from_node_id) REFERENCES aps_route_node (factory_id, route_version_id, id),
    CONSTRAINT fk_route_edge_to FOREIGN KEY (factory_id, route_version_id, to_node_id) REFERENCES aps_route_node (factory_id, route_version_id, id),
    CONSTRAINT fk_route_edge_calendar FOREIGN KEY (factory_id, lag_calendar_resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT ck_route_edge_nodes CHECK (from_node_id <> to_node_id),
    CONSTRAINT ck_route_edge_type CHECK (relation_type IN ('FINISH', 'QUANTITY', 'TRANSFER')),
    CONSTRAINT ck_route_edge_lag CHECK (lag_seconds >= 0),
    CONSTRAINT ck_route_edge_lag_basis CHECK (
        (lag_basis = 'ELAPSED' AND lag_calendar_resource_id IS NULL)
        OR (lag_basis = 'WORKING' AND lag_calendar_resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_route_edge_fields CHECK (
        (relation_type = 'FINISH' AND threshold_mode IS NULL AND threshold_qty IS NULL
            AND threshold_ratio IS NULL AND usage_ratio IS NULL AND transfer_qty IS NULL)
        OR
        (relation_type = 'QUANTITY' AND threshold_mode IS NOT NULL
            AND threshold_qty IS NOT NULL AND threshold_qty > 0
            AND usage_ratio IS NULL AND transfer_qty IS NULL
            AND ((threshold_mode = 'ABSOLUTE' AND threshold_ratio IS NULL)
                OR (threshold_mode = 'RATIO' AND threshold_ratio IS NOT NULL
                    AND threshold_ratio > 0 AND threshold_ratio <= 1)))
        OR
        (relation_type = 'TRANSFER' AND threshold_mode IS NULL AND threshold_qty IS NULL
            AND threshold_ratio IS NULL AND usage_ratio IS NOT NULL AND usage_ratio > 0
            AND transfer_qty IS NOT NULL AND transfer_qty > 0)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T17 路线模板关系';

-- T18 生产订单
CREATE TABLE aps_order (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    external_id VARCHAR(128) NULL,
    external_revision VARCHAR(64) NULL,
    customer_reference VARCHAR(200) NULL,
    priority INT NOT NULL DEFAULT 50,
    earliest_start_at DATETIME(3) NOT NULL,
    promised_at DATETIME(3) NOT NULL,
    original_promised_at DATETIME(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    cancellation_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_order_factory_id (factory_id, id),
    UNIQUE KEY uq_order_no (factory_id, order_no),
    UNIQUE KEY uq_order_external (factory_id, source_system, external_id),
    KEY ix_order_status_promise (factory_id, status, promised_at, priority),
    CONSTRAINT fk_order_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT ck_order_priority CHECK (priority BETWEEN 0 AND 100),
    CONSTRAINT ck_order_status CHECK (status IN ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_order_times CHECK (earliest_start_at <= promised_at),
    CONSTRAINT ck_order_cancel CHECK (status <> 'CANCELLED' OR cancellation_reason IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T18 生产订单';

-- T19 产品需求行
CREATE TABLE aps_order_line (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_no INT NOT NULL,
    item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ordered_qty DECIMAL(18, 6) NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promised_at DATETIME(3) NULL,
    external_line_id VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_order_line_factory_id (factory_id, id),
    UNIQUE KEY uq_order_line_no (factory_id, order_id, line_no),
    UNIQUE KEY uq_order_line_external (factory_id, order_id, external_line_id),
    KEY ix_order_line_route (factory_id, item_id, route_version_id),
    KEY ix_order_line_uom (factory_id, uom_id),
    CONSTRAINT fk_order_line_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_order_line_order FOREIGN KEY (factory_id, order_id) REFERENCES aps_order (factory_id, id),
    CONSTRAINT fk_order_line_item FOREIGN KEY (factory_id, item_id) REFERENCES aps_item (factory_id, id),
    CONSTRAINT fk_order_line_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT fk_order_line_route FOREIGN KEY (factory_id, item_id, route_version_id) REFERENCES aps_route_version (factory_id, item_id, id),
    CONSTRAINT ck_order_line_no CHECK (line_no > 0),
    CONSTRAINT ck_order_line_qty CHECK (ordered_qty > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T19 产品需求行';

-- T20 产品生产与恢复实例；对 T21/T43 的循环外键在文末补加。
CREATE TABLE aps_production_lot (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_line_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lot_no VARCHAR(64) NOT NULL,
    route_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lot_kind VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    planned_qty DECIMAL(18, 6) NOT NULL,
    source_disposition_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    return_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reentry_node_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_qty DECIMAL(18, 6) NULL,
    source_uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    product_qty DECIMAL(18, 6) NULL,
    conversion_ratio DECIMAL(18, 9) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_production_lot_factory_id (factory_id, id),
    UNIQUE KEY uq_production_lot_no (factory_id, lot_no),
    KEY ix_production_lot_order_line (factory_id, order_line_id),
    KEY ix_production_lot_disposition (factory_id, source_disposition_id),
    KEY ix_production_lot_source_task (factory_id, source_task_id),
    KEY ix_production_lot_return_task (factory_id, return_task_id),
    KEY ix_production_lot_reentry (factory_id, route_version_id, reentry_node_id),
    KEY ix_production_lot_source_uom (factory_id, source_uom_id),
    CONSTRAINT fk_production_lot_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_production_lot_order_line FOREIGN KEY (factory_id, order_line_id) REFERENCES aps_order_line (factory_id, id),
    CONSTRAINT fk_production_lot_route FOREIGN KEY (factory_id, route_version_id) REFERENCES aps_route_version (factory_id, id),
    CONSTRAINT fk_production_lot_reentry FOREIGN KEY (factory_id, route_version_id, reentry_node_id) REFERENCES aps_route_node (factory_id, route_version_id, id),
    CONSTRAINT fk_production_lot_source_uom FOREIGN KEY (factory_id, source_uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_production_lot_kind CHECK (lot_kind IN ('NORMAL', 'REWORK', 'REPLACEMENT')),
    CONSTRAINT ck_production_lot_qty CHECK (planned_qty > 0),
    CONSTRAINT ck_production_lot_status CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_production_lot_recovery CHECK (
        (lot_kind = 'NORMAL' AND source_disposition_id IS NULL AND source_task_id IS NULL
            AND return_task_id IS NULL AND reentry_node_id IS NULL AND source_qty IS NULL
            AND source_uom_id IS NULL AND product_qty IS NULL AND conversion_ratio IS NULL)
        OR
        (lot_kind IN ('REWORK', 'REPLACEMENT') AND source_disposition_id IS NOT NULL
            AND source_task_id IS NOT NULL AND reentry_node_id IS NOT NULL
            AND source_qty IS NOT NULL AND source_qty > 0 AND source_uom_id IS NOT NULL
            AND product_qty IS NOT NULL AND product_qty > 0
            AND conversion_ratio IS NOT NULL AND conversion_ratio > 0)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T20 产品生产与恢复实例';

-- T21 稳定工序任务身份；current_spec_id 的循环外键在文末补加。
CREATE TABLE aps_task (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    production_lot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    route_node_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    current_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    responsible_user_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_task_factory_id (factory_id, id),
    UNIQUE KEY uq_task_no (factory_id, task_no),
    KEY ix_task_lot_status (factory_id, production_lot_id, status),
    KEY ix_task_current_spec (factory_id, id, current_spec_id),
    KEY ix_task_route_node (factory_id, route_node_id),
    CONSTRAINT fk_task_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_task_lot FOREIGN KEY (factory_id, production_lot_id) REFERENCES aps_production_lot (factory_id, id),
    CONSTRAINT fk_task_route_node FOREIGN KEY (factory_id, route_node_id) REFERENCES aps_route_node (factory_id, id),
    CONSTRAINT ck_task_status CHECK (status IN ('READY', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T21 稳定工序任务身份';

-- T22 任务需求快照
CREATE TABLE aps_task_spec (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    spec_no INT NOT NULL,
    operation_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required_qty DECIMAL(18, 6) NOT NULL,
    output_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    earliest_start_at DATETIME(3) NOT NULL,
    required_for_completion BOOLEAN NOT NULL DEFAULT TRUE,
    is_delivery_output BOOLEAN NOT NULL DEFAULT FALSE,
    is_recovery_output BOOLEAN NOT NULL DEFAULT FALSE,
    change_reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_task_spec_factory_id (factory_id, id),
    UNIQUE KEY uq_task_spec_task_id (factory_id, task_id, id),
    UNIQUE KEY uq_task_spec_no (factory_id, task_id, spec_no),
    KEY ix_task_spec_operation (factory_id, operation_spec_id),
    KEY ix_task_spec_output_item (factory_id, output_item_id),
    KEY ix_task_spec_uom (factory_id, uom_id),
    CONSTRAINT fk_task_spec_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_task_spec_task FOREIGN KEY (factory_id, task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_task_spec_operation FOREIGN KEY (factory_id, operation_spec_id) REFERENCES aps_operation_spec (factory_id, id),
    CONSTRAINT fk_task_spec_item FOREIGN KEY (factory_id, output_item_id) REFERENCES aps_item (factory_id, id),
    CONSTRAINT fk_task_spec_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_task_spec_no CHECK (spec_no > 0),
    CONSTRAINT ck_task_spec_qty CHECK (required_qty > 0),
    CONSTRAINT ck_task_spec_flags CHECK (
        required_for_completion IN (0, 1) AND is_delivery_output IN (0, 1) AND is_recovery_output IN (0, 1)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T22 任务需求快照';

-- T23 非消耗前置关系
CREATE TABLE aps_task_dependency (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    to_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    to_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'FINISH',
    threshold_mode VARCHAR(32) NULL,
    denominator_qty DECIMAL(18, 6) NULL,
    threshold_ratio DECIMAL(18, 9) NULL,
    threshold_qty DECIMAL(18, 6) NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lag_seconds BIGINT NOT NULL DEFAULT 0,
    lag_basis VARCHAR(32) NOT NULL DEFAULT 'ELAPSED',
    lag_calendar_resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_task_dependency_factory_id (factory_id, id),
    UNIQUE KEY uq_task_dependency_pair (factory_id, from_task_id, to_task_id, relation_type),
    KEY ix_task_dependency_from_spec (factory_id, from_task_id, from_spec_id),
    KEY ix_task_dependency_to_spec (factory_id, to_task_id, to_spec_id),
    KEY ix_task_dependency_uom (factory_id, uom_id),
    KEY ix_task_dependency_calendar (factory_id, lag_calendar_resource_id),
    CONSTRAINT fk_task_dependency_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_task_dependency_from_task FOREIGN KEY (factory_id, from_task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_task_dependency_to_task FOREIGN KEY (factory_id, to_task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_task_dependency_from_spec FOREIGN KEY (factory_id, from_task_id, from_spec_id) REFERENCES aps_task_spec (factory_id, task_id, id),
    CONSTRAINT fk_task_dependency_to_spec FOREIGN KEY (factory_id, to_task_id, to_spec_id) REFERENCES aps_task_spec (factory_id, task_id, id),
    CONSTRAINT fk_task_dependency_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT fk_task_dependency_calendar FOREIGN KEY (factory_id, lag_calendar_resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT ck_task_dependency_nodes CHECK (from_task_id <> to_task_id),
    CONSTRAINT ck_task_dependency_lag CHECK (lag_seconds >= 0),
    CONSTRAINT ck_task_dependency_basis CHECK (
        (lag_basis = 'ELAPSED' AND lag_calendar_resource_id IS NULL)
        OR (lag_basis = 'WORKING' AND lag_calendar_resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_task_dependency_fields CHECK (
        (relation_type = 'FINISH' AND threshold_mode IS NULL AND denominator_qty IS NULL
            AND threshold_ratio IS NULL AND threshold_qty IS NULL AND uom_id IS NULL)
        OR
        (relation_type = 'QUANTITY' AND threshold_mode IS NOT NULL
            AND denominator_qty IS NOT NULL AND denominator_qty > 0
            AND threshold_qty IS NOT NULL AND threshold_qty > 0 AND uom_id IS NOT NULL
            AND ((threshold_mode = 'ABSOLUTE' AND threshold_ratio IS NULL)
                OR (threshold_mode = 'RATIO' AND threshold_ratio IS NOT NULL
                    AND threshold_ratio > 0 AND threshold_ratio <= 1)))
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T23 非消耗前置关系';

-- T24 工序消耗需求
CREATE TABLE aps_material_demand (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    demand_no INT NOT NULL,
    item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required_source_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    usage_ratio DECIMAL(18, 9) NOT NULL,
    total_required_qty DECIMAL(18, 6) NOT NULL,
    first_transfer_qty DECIMAL(18, 6) NOT NULL,
    transfer_qty DECIMAL(18, 6) NOT NULL,
    lag_seconds BIGINT NOT NULL DEFAULT 0,
    lag_basis VARCHAR(32) NOT NULL DEFAULT 'ELAPSED',
    lag_calendar_resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_material_demand_factory_id (factory_id, id),
    UNIQUE KEY uq_material_demand_no (factory_id, task_spec_id, demand_no),
    KEY ix_material_demand_item (factory_id, item_id),
    KEY ix_material_demand_source_task (factory_id, required_source_task_id),
    KEY ix_material_demand_task_spec (factory_id, task_id, task_spec_id),
    KEY ix_material_demand_uom (factory_id, uom_id),
    KEY ix_material_demand_calendar (factory_id, lag_calendar_resource_id),
    CONSTRAINT fk_material_demand_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_material_demand_task FOREIGN KEY (factory_id, task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_material_demand_spec FOREIGN KEY (factory_id, task_id, task_spec_id) REFERENCES aps_task_spec (factory_id, task_id, id),
    CONSTRAINT fk_material_demand_item FOREIGN KEY (factory_id, item_id) REFERENCES aps_item (factory_id, id),
    CONSTRAINT fk_material_demand_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT fk_material_demand_source_task FOREIGN KEY (factory_id, required_source_task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_material_demand_calendar FOREIGN KEY (factory_id, lag_calendar_resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT ck_material_demand_no CHECK (demand_no > 0),
    CONSTRAINT ck_material_demand_qty CHECK (
        usage_ratio > 0 AND total_required_qty > 0 AND first_transfer_qty > 0
        AND transfer_qty > 0 AND first_transfer_qty <= total_required_qty
    ),
    CONSTRAINT ck_material_demand_lag CHECK (lag_seconds >= 0),
    CONSTRAINT ck_material_demand_basis CHECK (
        (lag_basis = 'ELAPSED' AND lag_calendar_resource_id IS NULL)
        OR (lag_basis = 'WORKING' AND lag_calendar_resource_id IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T24 工序消耗需求';

-- T25 工厂调度范围与正式指针；current_version_id 的循环外键在文末补加。
CREATE TABLE aps_plan_scope (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_code VARCHAR(64) NOT NULL DEFAULT 'FACTORY',
    current_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    definition_revision BIGINT NOT NULL DEFAULT 0,
    execution_revision BIGINT NOT NULL DEFAULT 0,
    publication_no BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_scope_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_scope_factory (factory_id),
    KEY ix_plan_scope_current (factory_id, id, current_version_id),
    CONSTRAINT fk_plan_scope_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT ck_plan_scope_revisions CHECK (
        definition_revision >= 0 AND execution_revision >= 0 AND publication_no >= 0
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T25 工厂调度范围与正式指针';

-- T26 计划版本、输入快照与校验摘要
CREATE TABLE aps_plan_version (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_no BIGINT NOT NULL,
    base_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    input_definition_revision BIGINT NOT NULL,
    input_execution_revision BIGINT NOT NULL,
    horizon_start DATETIME(3) NOT NULL,
    horizon_end DATETIME(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    solver_name VARCHAR(128) NOT NULL,
    strategy_json JSON NOT NULL,
    result_kind VARCHAR(32) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    input_schema_version INT NOT NULL DEFAULT 1,
    input_captured_at DATETIME(3) NOT NULL,
    input_content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_snapshot_json JSON NOT NULL,
    validation_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'NOT_RUN',
    validated_at DATETIME(3) NULL,
    validated_content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    validation_summary_json JSON NULL,
    published_at DATETIME(3) NULL,
    published_by BIGINT NULL,
    publish_note VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_version_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_version_scope_id (factory_id, scope_id, id),
    UNIQUE KEY uq_plan_version_no (factory_id, scope_id, version_no),
    KEY ix_plan_version_status (factory_id, scope_id, status),
    KEY ix_plan_version_base (factory_id, scope_id, base_version_id),
    CONSTRAINT fk_plan_version_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_version_scope FOREIGN KEY (factory_id, scope_id) REFERENCES aps_plan_scope (factory_id, id),
    CONSTRAINT fk_plan_version_base FOREIGN KEY (factory_id, scope_id, base_version_id) REFERENCES aps_plan_version (factory_id, scope_id, id),
    CONSTRAINT ck_plan_version_no CHECK (version_no > 0),
    CONSTRAINT ck_plan_version_revisions CHECK (input_definition_revision >= 0 AND input_execution_revision >= 0),
    CONSTRAINT ck_plan_version_horizon CHECK (horizon_start < horizon_end),
    CONSTRAINT ck_plan_version_status CHECK (status IN ('DRAFT', 'SOLVING', 'FEASIBLE', 'CONFLICT', 'CANCELLED', 'FAILED', 'PUBLISHED', 'SUPERSEDED')),
    CONSTRAINT ck_plan_version_result CHECK (result_kind IS NULL OR result_kind IN ('FEASIBLE', 'INFEASIBLE_PROVEN', 'TIMEOUT_WITH_SOLUTION', 'TIMEOUT_NO_SOLUTION', 'INVALID_INPUT')),
    CONSTRAINT ck_plan_version_validation CHECK (
        (validation_status = 'NOT_RUN' AND validation_run_id IS NULL AND validated_at IS NULL
            AND validated_content_sha256 IS NULL AND validation_summary_json IS NULL)
        OR
        (validation_status IN ('PASS', 'FAIL') AND validation_run_id IS NOT NULL
            AND validated_at IS NOT NULL AND validated_content_sha256 IS NOT NULL
            AND validation_summary_json IS NOT NULL)
    ),
    CONSTRAINT ck_plan_version_schema CHECK (input_schema_version > 0),
    CONSTRAINT ck_plan_version_finish CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
    CONSTRAINT ck_plan_version_publish CHECK (
        status <> 'PUBLISHED'
        OR (validation_status = 'PASS' AND validated_at IS NOT NULL
            AND validated_content_sha256 IS NOT NULL AND published_at IS NOT NULL AND published_by IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T26 计划版本、输入快照与校验摘要';

-- T27 计划作业与共享加工批；carry_run_id 的循环外键在文末补加。
CREATE TABLE aps_plan_job (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    job_code VARCHAR(64) NOT NULL,
    job_type VARCHAR(32) NOT NULL DEFAULT 'SINGLE',
    cycle_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_center_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    compatibility_key VARCHAR(128) NULL,
    carry_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    planned_start DATETIME(3) NOT NULL,
    planned_end DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_job_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_job_version_id (factory_id, plan_version_id, id),
    UNIQUE KEY uq_plan_job_code (factory_id, plan_version_id, job_code),
    UNIQUE KEY uq_plan_job_carry_run (factory_id, carry_run_id, plan_version_id),
    KEY ix_plan_job_center_time (factory_id, work_center_id, planned_start, planned_end),
    KEY ix_plan_job_spec (factory_id, cycle_spec_id),
    CONSTRAINT fk_plan_job_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_job_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_plan_job_spec FOREIGN KEY (factory_id, cycle_spec_id) REFERENCES aps_operation_spec (factory_id, id),
    CONSTRAINT fk_plan_job_center FOREIGN KEY (factory_id, work_center_id) REFERENCES aps_work_center (factory_id, id),
    CONSTRAINT ck_plan_job_type CHECK (job_type IN ('SINGLE', 'SHARED_BATCH')),
    CONSTRAINT ck_plan_job_times CHECK (planned_start < planned_end),
    CONSTRAINT ck_plan_job_compatibility CHECK (
        (job_type = 'SINGLE' AND compatibility_key IS NULL)
        OR (job_type = 'SHARED_BATCH' AND compatibility_key IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T27 计划作业与共享加工批';

-- T28 作业成员和分配量
CREATE TABLE aps_plan_job_member (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    allocated_qty DECIMAL(18, 6) NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_job_member_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_member_hierarchy (factory_id, plan_version_id, plan_job_id, id),
    UNIQUE KEY uq_plan_job_member_task (factory_id, plan_job_id, task_id),
    KEY ix_plan_job_member_task_spec (factory_id, task_id, task_spec_id),
    KEY ix_plan_job_member_version_task (factory_id, plan_version_id, task_id),
    KEY ix_plan_job_member_uom (factory_id, uom_id),
    CONSTRAINT fk_plan_job_member_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_job_member_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_plan_job_member_job FOREIGN KEY (factory_id, plan_version_id, plan_job_id) REFERENCES aps_plan_job (factory_id, plan_version_id, id),
    CONSTRAINT fk_plan_job_member_task FOREIGN KEY (factory_id, task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_plan_job_member_spec FOREIGN KEY (factory_id, task_id, task_spec_id) REFERENCES aps_task_spec (factory_id, task_id, id),
    CONSTRAINT fk_plan_job_member_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_plan_job_member_qty CHECK (allocated_qty > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T28 作业成员和分配量';

-- T29 计划阶段与中断分段
CREATE TABLE aps_plan_segment (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phase_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    segment_no INT NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    work_qty DECIMAL(18, 6) NULL,
    duration_mode_snapshot VARCHAR(32) NOT NULL,
    fixed_seconds_snapshot BIGINT NOT NULL,
    seconds_per_unit_snapshot DECIMAL(18, 9) NOT NULL,
    planned_duration_seconds BIGINT NOT NULL,
    rate_uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    calculation_basis_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_segment_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_segment_version_id (factory_id, plan_version_id, id),
    UNIQUE KEY uq_plan_segment_hierarchy (factory_id, plan_version_id, plan_job_id, id),
    UNIQUE KEY uq_plan_segment_no (factory_id, plan_job_id, segment_no),
    KEY ix_plan_segment_version_time (factory_id, plan_version_id, start_at, end_at),
    KEY ix_plan_segment_phase (factory_id, phase_id),
    KEY ix_plan_segment_rate_uom (factory_id, rate_uom_id),
    CONSTRAINT fk_plan_segment_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_segment_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_plan_segment_job FOREIGN KEY (factory_id, plan_version_id, plan_job_id) REFERENCES aps_plan_job (factory_id, plan_version_id, id),
    CONSTRAINT fk_plan_segment_phase FOREIGN KEY (factory_id, phase_id) REFERENCES aps_operation_phase (factory_id, id),
    CONSTRAINT fk_plan_segment_uom FOREIGN KEY (factory_id, rate_uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_plan_segment_no CHECK (segment_no > 0),
    CONSTRAINT ck_plan_segment_times CHECK (start_at < end_at),
    CONSTRAINT ck_plan_segment_mode CHECK (duration_mode_snapshot IN ('FIXED', 'PER_UNIT')),
    CONSTRAINT ck_plan_segment_duration CHECK (
        fixed_seconds_snapshot >= 0 AND seconds_per_unit_snapshot >= 0
        AND planned_duration_seconds > 0
        AND ((duration_mode_snapshot = 'FIXED' AND work_qty IS NULL)
            OR (duration_mode_snapshot = 'PER_UNIT' AND work_qty IS NOT NULL AND work_qty > 0
                AND seconds_per_unit_snapshot > 0))
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T29 计划阶段与中断分段';

-- T30 阶段具体资源预约
CREATE TABLE aps_plan_allocation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_segment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requirement_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seat_no INT NOT NULL,
    resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    demand_units DECIMAL(18, 6) NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    exclusive_snapshot BOOLEAN NOT NULL,
    enforce_exclusive BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_allocation_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_allocation_seat (factory_id, plan_segment_id, requirement_id, seat_no),
    KEY ix_plan_allocation_segment (factory_id, plan_version_id, plan_segment_id),
    KEY ix_plan_allocation_resource_time (factory_id, resource_id, start_at, end_at),
    KEY ix_plan_allocation_requirement (factory_id, requirement_id),
    KEY ix_plan_allocation_uom (factory_id, uom_id),
    CONSTRAINT fk_plan_allocation_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_allocation_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_plan_allocation_segment FOREIGN KEY (factory_id, plan_version_id, plan_segment_id) REFERENCES aps_plan_segment (factory_id, plan_version_id, id),
    CONSTRAINT fk_plan_allocation_requirement FOREIGN KEY (factory_id, requirement_id) REFERENCES aps_resource_requirement (factory_id, id),
    CONSTRAINT fk_plan_allocation_resource FOREIGN KEY (factory_id, resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT fk_plan_allocation_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_plan_allocation_seat CHECK (seat_no > 0),
    CONSTRAINT ck_plan_allocation_times CHECK (start_at < end_at),
    CONSTRAINT ck_plan_allocation_units CHECK (demand_units > 0),
    CONSTRAINT ck_plan_allocation_flags CHECK (exclusive_snapshot IN (0, 1) AND enforce_exclusive IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T30 阶段具体资源预约';

-- T31 逐成员预测释放
CREATE TABLE aps_plan_output (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_segment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_member_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    release_no INT NOT NULL,
    quantity DECIMAL(18, 6) NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    predicted_release_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_output_factory_id (factory_id, id),
    UNIQUE KEY uq_plan_output_version_id (factory_id, plan_version_id, id),
    UNIQUE KEY uq_plan_output_release (factory_id, plan_member_id, release_no),
    KEY ix_plan_output_segment (factory_id, plan_version_id, plan_job_id, plan_segment_id),
    KEY ix_plan_output_member (factory_id, plan_version_id, plan_job_id, plan_member_id),
    KEY ix_plan_output_version_time (factory_id, plan_version_id, predicted_release_at),
    KEY ix_plan_output_uom (factory_id, uom_id),
    CONSTRAINT fk_plan_output_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_output_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_plan_output_job FOREIGN KEY (factory_id, plan_version_id, plan_job_id) REFERENCES aps_plan_job (factory_id, plan_version_id, id),
    CONSTRAINT fk_plan_output_segment FOREIGN KEY (factory_id, plan_version_id, plan_job_id, plan_segment_id) REFERENCES aps_plan_segment (factory_id, plan_version_id, plan_job_id, id),
    CONSTRAINT fk_plan_output_member FOREIGN KEY (factory_id, plan_version_id, plan_job_id, plan_member_id) REFERENCES aps_plan_job_member (factory_id, plan_version_id, plan_job_id, id),
    CONSTRAINT fk_plan_output_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_plan_output_release CHECK (release_no > 0),
    CONSTRAINT ck_plan_output_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T31 逐成员预测释放';

-- T32 计划态供需配对；source_output_lot_id 的前向外键在文末补加。
CREATE TABLE aps_plan_supply (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    demand_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    supply_output_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_output_lot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    allocated_qty DECIMAL(18, 6) NOT NULL,
    expected_available_at DATETIME(3) NOT NULL,
    required_at DATETIME(3) NOT NULL,
    source_confidence VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_supply_factory_id (factory_id, id),
    KEY ix_plan_supply_demand (factory_id, plan_version_id, demand_id),
    KEY ix_plan_supply_demand_fk (factory_id, demand_id),
    KEY ix_plan_supply_output (factory_id, plan_version_id, supply_output_id),
    KEY ix_plan_supply_output_lot (factory_id, source_output_lot_id),
    CONSTRAINT fk_plan_supply_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_supply_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_plan_supply_demand FOREIGN KEY (factory_id, demand_id) REFERENCES aps_material_demand (factory_id, id),
    CONSTRAINT fk_plan_supply_output FOREIGN KEY (factory_id, plan_version_id, supply_output_id) REFERENCES aps_plan_output (factory_id, plan_version_id, id),
    CONSTRAINT ck_plan_supply_source CHECK ((supply_output_id IS NULL) <> (source_output_lot_id IS NULL)),
    CONSTRAINT ck_plan_supply_qty CHECK (allocated_qty > 0),
    CONSTRAINT ck_plan_supply_time CHECK (expected_available_at <= required_at),
    CONSTRAINT ck_plan_supply_confidence CHECK (source_confidence IN ('CONFIRMED', 'FORECAST'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T32 计划态供需配对';

-- T33 人工锁定锚点
CREATE TABLE aps_plan_lock (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    anchor_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lock_time BOOLEAN NOT NULL DEFAULT FALSE,
    lock_resource BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plan_lock_factory_id (factory_id, id),
    KEY ix_plan_lock_active (factory_id, scope_id, task_id, active),
    KEY ix_plan_lock_version (factory_id, scope_id, anchor_version_id),
    KEY ix_plan_lock_task (factory_id, task_id),
    CONSTRAINT fk_plan_lock_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_plan_lock_scope FOREIGN KEY (factory_id, scope_id) REFERENCES aps_plan_scope (factory_id, id),
    CONSTRAINT fk_plan_lock_task FOREIGN KEY (factory_id, task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_plan_lock_version FOREIGN KEY (factory_id, scope_id, anchor_version_id) REFERENCES aps_plan_version (factory_id, scope_id, id),
    CONSTRAINT ck_plan_lock_kind CHECK (
        lock_time IN (0, 1) AND lock_resource IN (0, 1) AND active IN (0, 1)
        AND (lock_time = 1 OR lock_resource = 1)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T33 人工锁定锚点';

-- T34 冻结日计划基线（追加事实）
CREATE TABLE aps_daily_baseline (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workshop_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    production_date DATE NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    frozen_at DATETIME(3) NOT NULL,
    frozen_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_daily_baseline_factory_id (factory_id, id),
    UNIQUE KEY uq_daily_baseline_date (factory_id, workshop_id, production_date),
    KEY ix_daily_baseline_version (factory_id, plan_version_id),
    CONSTRAINT fk_daily_baseline_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_daily_baseline_workshop FOREIGN KEY (factory_id, workshop_id) REFERENCES aps_workshop (factory_id, id),
    CONSTRAINT fk_daily_baseline_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T34 冻结日计划基线';

-- T35 一次真实作业运行
CREATE TABLE aps_execution_run (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    source_plan_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_center_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    actual_start DATETIME(3) NOT NULL,
    actual_end DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_execution_run_factory_id (factory_id, id),
    UNIQUE KEY uq_execution_run_no (factory_id, run_no),
    UNIQUE KEY uq_execution_run_plan_job (factory_id, source_plan_job_id),
    KEY ix_execution_run_plan_job_version (factory_id, source_plan_version_id, source_plan_job_id),
    KEY ix_execution_run_status (factory_id, work_center_id, status, actual_start),
    CONSTRAINT fk_execution_run_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_execution_run_job FOREIGN KEY (factory_id, source_plan_version_id, source_plan_job_id) REFERENCES aps_plan_job (factory_id, plan_version_id, id),
    CONSTRAINT fk_execution_run_version FOREIGN KEY (factory_id, source_plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT fk_execution_run_center FOREIGN KEY (factory_id, work_center_id) REFERENCES aps_work_center (factory_id, id),
    CONSTRAINT ck_execution_run_status CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'ABORTED')),
    CONSTRAINT ck_execution_run_end CHECK (
        (status IN ('RUNNING', 'PAUSED') AND actual_end IS NULL)
        OR (status IN ('COMPLETED', 'ABORTED') AND actual_end IS NOT NULL AND actual_end >= actual_start)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T35 一次真实作业运行';

-- T36 真实运行成员（追加事实）
CREATE TABLE aps_run_member (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_plan_member_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_spec_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_qty DECIMAL(18, 6) NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_run_member_factory_id (factory_id, id),
    UNIQUE KEY uq_run_member_task (factory_id, execution_run_id, task_id),
    UNIQUE KEY uq_run_member_source (factory_id, source_plan_member_id, execution_run_id),
    KEY ix_run_member_task_spec (factory_id, task_id, task_spec_id),
    KEY ix_run_member_uom (factory_id, uom_id),
    CONSTRAINT fk_run_member_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_run_member_run FOREIGN KEY (factory_id, execution_run_id) REFERENCES aps_execution_run (factory_id, id),
    CONSTRAINT fk_run_member_plan_member FOREIGN KEY (factory_id, source_plan_member_id) REFERENCES aps_plan_job_member (factory_id, id),
    CONSTRAINT fk_run_member_task FOREIGN KEY (factory_id, task_id) REFERENCES aps_task (factory_id, id),
    CONSTRAINT fk_run_member_spec FOREIGN KEY (factory_id, task_id, task_spec_id) REFERENCES aps_task_spec (factory_id, task_id, id),
    CONSTRAINT fk_run_member_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_run_member_qty CHECK (assigned_qty > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T36 真实运行成员';

-- T37 实际资源占用区间；voided_by_event_id 的前向外键在文末补加。
CREATE TABLE aps_actual_occupancy (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phase_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    seat_no INT NOT NULL DEFAULT 1,
    occupancy_kind VARCHAR(32) NOT NULL DEFAULT 'WORK',
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NULL,
    demand_units DECIMAL(18, 6) NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correction_of_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    voided_by_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_actual_occupancy_factory_id (factory_id, id),
    UNIQUE KEY uq_actual_occupancy_correction (factory_id, correction_of_id),
    KEY ix_actual_occupancy_resource (factory_id, resource_id, end_at, start_at),
    KEY ix_actual_occupancy_run (factory_id, execution_run_id, start_at),
    KEY ix_actual_occupancy_void (factory_id, voided_by_event_id),
    KEY ix_actual_occupancy_phase (factory_id, phase_id),
    KEY ix_actual_occupancy_uom (factory_id, uom_id),
    CONSTRAINT fk_actual_occupancy_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_actual_occupancy_run FOREIGN KEY (factory_id, execution_run_id) REFERENCES aps_execution_run (factory_id, id),
    CONSTRAINT fk_actual_occupancy_phase FOREIGN KEY (factory_id, phase_id) REFERENCES aps_operation_phase (factory_id, id),
    CONSTRAINT fk_actual_occupancy_resource FOREIGN KEY (factory_id, resource_id) REFERENCES aps_resource (factory_id, id),
    CONSTRAINT fk_actual_occupancy_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT fk_actual_occupancy_correction FOREIGN KEY (factory_id, correction_of_id) REFERENCES aps_actual_occupancy (factory_id, id),
    CONSTRAINT ck_actual_occupancy_self CHECK (correction_of_id IS NULL OR correction_of_id <> id),
    CONSTRAINT ck_actual_occupancy_time CHECK (end_at IS NULL OR end_at > start_at),
    CONSTRAINT ck_actual_occupancy_seat CHECK (seat_no > 0),
    CONSTRAINT ck_actual_occupancy_kind CHECK (occupancy_kind IN ('WORK', 'HOLD')),
    CONSTRAINT ck_actual_occupancy_units CHECK (demand_units > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T37 实际资源占用区间';

-- T38 数量报工（追加事实）；inbox_id 的前向外键在文末补加。
CREATE TABLE aps_production_report (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_member_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inbox_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    produced_qty DECIMAL(18, 6) NOT NULL,
    immediate_good_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    pending_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    rejected_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correction_of_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_production_report_factory_id (factory_id, id),
    UNIQUE KEY uq_production_report_command (factory_id, inbox_id, run_member_id),
    UNIQUE KEY uq_production_report_correction (factory_id, correction_of_id),
    KEY ix_production_report_member_time (factory_id, run_member_id, occurred_at),
    KEY ix_production_report_uom (factory_id, uom_id),
    CONSTRAINT fk_production_report_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_production_report_member FOREIGN KEY (factory_id, run_member_id) REFERENCES aps_run_member (factory_id, id),
    CONSTRAINT fk_production_report_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT fk_production_report_correction FOREIGN KEY (factory_id, correction_of_id) REFERENCES aps_production_report (factory_id, id),
    CONSTRAINT ck_production_report_self CHECK (correction_of_id IS NULL OR correction_of_id <> id),
    CONSTRAINT ck_production_report_qty CHECK (
        produced_qty > 0 AND immediate_good_qty >= 0 AND pending_qty >= 0 AND rejected_qty >= 0
        AND produced_qty = immediate_good_qty + pending_qty + rejected_qty
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T38 数量报工';

-- T39 实物产出批与余额投影
CREATE TABLE aps_output_lot (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lot_no VARCHAR(64) NOT NULL,
    production_report_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    external_lot_id VARCHAR(128) NULL,
    item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uom_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    gross_qty DECIMAL(18, 6) NOT NULL,
    expected_available_at DATETIME(3) NULL,
    receipt_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    pending_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    available_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    reserved_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    consumed_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    rejected_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    rework_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    scrap_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    delivered_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    voided_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_output_lot_factory_id (factory_id, id),
    UNIQUE KEY uq_output_lot_no (factory_id, lot_no),
    UNIQUE KEY uq_output_lot_report (factory_id, production_report_id),
    UNIQUE KEY uq_output_lot_external (factory_id, source_system, external_lot_id),
    KEY ix_output_lot_available (factory_id, item_id, receipt_status, available_qty, expected_available_at),
    KEY ix_output_lot_uom (factory_id, uom_id),
    CONSTRAINT fk_output_lot_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_output_lot_report FOREIGN KEY (factory_id, production_report_id) REFERENCES aps_production_report (factory_id, id),
    CONSTRAINT fk_output_lot_item FOREIGN KEY (factory_id, item_id) REFERENCES aps_item (factory_id, id),
    CONSTRAINT fk_output_lot_uom FOREIGN KEY (factory_id, uom_id) REFERENCES aps_uom (factory_id, id),
    CONSTRAINT ck_output_lot_source CHECK ((production_report_id IS NULL) <> (external_lot_id IS NULL)),
    CONSTRAINT ck_output_lot_status CHECK (receipt_status IN ('EXPECTED', 'RECEIVED')),
    CONSTRAINT ck_output_lot_balances CHECK (
        gross_qty > 0 AND pending_qty >= 0 AND available_qty >= 0 AND reserved_qty >= 0
        AND consumed_qty >= 0 AND rejected_qty >= 0 AND rework_qty >= 0 AND scrap_qty >= 0
        AND delivered_qty >= 0 AND voided_qty >= 0
        AND ((receipt_status = 'EXPECTED' AND pending_qty = 0 AND available_qty = 0 AND reserved_qty = 0
            AND consumed_qty = 0 AND rejected_qty = 0 AND rework_qty = 0 AND scrap_qty = 0
            AND delivered_qty = 0 AND voided_qty = 0)
          OR (receipt_status = 'RECEIVED' AND gross_qty = pending_qty + available_qty + reserved_qty
            + consumed_qty + rejected_qty + rework_qty + scrap_qty + delivered_qty + voided_qty))
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T39 实物产出批与余额投影';

-- T40 数量状态转移与交付账（追加事实）；inbox_id/reservation_id 的前向外键在文末补加。
CREATE TABLE aps_quantity_event (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_lot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inbox_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_no INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    reservation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    order_line_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    from_bucket VARCHAR(32) NULL,
    to_bucket VARCHAR(32) NOT NULL,
    quantity DECIMAL(18, 6) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    reversal_of_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    quality_reference VARCHAR(128) NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_quantity_event_factory_id (factory_id, id),
    UNIQUE KEY uq_quantity_event_entry (factory_id, inbox_id, entry_no),
    UNIQUE KEY uq_quantity_event_reversal (factory_id, reversal_of_id),
    KEY ix_quantity_event_lot_time (factory_id, output_lot_id, occurred_at),
    KEY ix_quantity_event_reservation (factory_id, reservation_id),
    KEY ix_quantity_event_order_line (factory_id, order_line_id, occurred_at),
    CONSTRAINT fk_quantity_event_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_quantity_event_lot FOREIGN KEY (factory_id, output_lot_id) REFERENCES aps_output_lot (factory_id, id),
    CONSTRAINT fk_quantity_event_order_line FOREIGN KEY (factory_id, order_line_id) REFERENCES aps_order_line (factory_id, id),
    CONSTRAINT fk_quantity_event_reversal FOREIGN KEY (factory_id, reversal_of_id) REFERENCES aps_quantity_event (factory_id, id),
    CONSTRAINT ck_quantity_event_entry CHECK (entry_no > 0),
    CONSTRAINT ck_quantity_event_type CHECK (event_type IN ('RECEIVE', 'RELEASE', 'RESERVE', 'UNRESERVE', 'CONSUME', 'DISPOSE', 'DELIVER', 'VOID', 'REVERSE')),
    CONSTRAINT ck_quantity_event_buckets CHECK (
        to_bucket IN ('PENDING', 'AVAILABLE', 'RESERVED', 'CONSUMED', 'REJECTED', 'REWORK', 'SCRAP', 'DELIVERED', 'VOIDED')
        AND (from_bucket IS NULL OR from_bucket IN ('PENDING', 'AVAILABLE', 'RESERVED', 'CONSUMED', 'REJECTED', 'REWORK', 'SCRAP', 'DELIVERED', 'VOIDED'))
        AND (from_bucket IS NULL OR from_bucket <> to_bucket)
    ),
    CONSTRAINT ck_quantity_event_qty CHECK (quantity > 0),
    CONSTRAINT ck_quantity_event_self CHECK (reversal_of_id IS NULL OR reversal_of_id <> id),
    CONSTRAINT ck_quantity_event_reservation CHECK (
        (event_type IN ('RESERVE', 'UNRESERVE', 'CONSUME') AND reservation_id IS NOT NULL)
        OR (event_type NOT IN ('RESERVE', 'UNRESERVE', 'CONSUME') AND reservation_id IS NULL)
    ),
    CONSTRAINT ck_quantity_event_delivery CHECK (
        (event_type = 'DELIVER' AND order_line_id IS NOT NULL AND from_bucket IS NOT NULL
            AND from_bucket = 'AVAILABLE' AND to_bucket = 'DELIVERED')
        OR (event_type <> 'DELIVER' AND (order_line_id IS NULL OR event_type = 'REVERSE'))
    ),
    CONSTRAINT ck_quantity_event_reverse CHECK (
        (event_type = 'REVERSE' AND reversal_of_id IS NOT NULL)
        OR (event_type <> 'REVERSE' AND reversal_of_id IS NULL)
    ),
    CONSTRAINT ck_quantity_event_flow CHECK (
        (event_type = 'RECEIVE' AND from_bucket IS NULL AND to_bucket IN ('PENDING', 'AVAILABLE', 'REJECTED'))
        OR (event_type = 'RELEASE' AND from_bucket IS NOT NULL AND from_bucket = 'PENDING' AND to_bucket = 'AVAILABLE')
        OR (event_type = 'RESERVE' AND from_bucket IS NOT NULL AND from_bucket IN ('AVAILABLE', 'REWORK') AND to_bucket = 'RESERVED')
        OR (event_type = 'UNRESERVE' AND from_bucket IS NOT NULL AND from_bucket = 'RESERVED' AND to_bucket IN ('AVAILABLE', 'REWORK'))
        OR (event_type = 'CONSUME' AND from_bucket IS NOT NULL AND from_bucket = 'RESERVED' AND to_bucket = 'CONSUMED')
        OR (event_type = 'DISPOSE' AND from_bucket IS NOT NULL AND from_bucket = 'REJECTED' AND to_bucket IN ('REWORK', 'SCRAP'))
        OR (event_type = 'DELIVER' AND from_bucket IS NOT NULL AND from_bucket = 'AVAILABLE' AND to_bucket = 'DELIVERED')
        OR (event_type = 'VOID' AND from_bucket IS NOT NULL AND to_bucket = 'VOIDED')
        OR (event_type = 'REVERSE' AND from_bucket IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T40 数量状态转移与交付账';

-- T41 实际供给预留；inbox_id 的前向外键在文末补加。
CREATE TABLE aps_supply_reservation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    demand_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_lot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_supply_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reservation_kind VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    origin_bucket VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    reserved_qty DECIMAL(18, 6) NOT NULL,
    consumed_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    released_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    inbox_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_supply_reservation_factory_id (factory_id, id),
    UNIQUE KEY uq_supply_reservation_command (factory_id, inbox_id, demand_id, output_lot_id),
    KEY ix_supply_reservation_demand (factory_id, demand_id, status),
    KEY ix_supply_reservation_lot (factory_id, output_lot_id, status),
    KEY ix_supply_reservation_plan (factory_id, plan_supply_id),
    CONSTRAINT fk_supply_reservation_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_supply_reservation_demand FOREIGN KEY (factory_id, demand_id) REFERENCES aps_material_demand (factory_id, id),
    CONSTRAINT fk_supply_reservation_lot FOREIGN KEY (factory_id, output_lot_id) REFERENCES aps_output_lot (factory_id, id),
    CONSTRAINT fk_supply_reservation_plan FOREIGN KEY (factory_id, plan_supply_id) REFERENCES aps_plan_supply (factory_id, id),
    CONSTRAINT ck_supply_reservation_kind CHECK (reservation_kind IN ('NORMAL', 'REWORK_INPUT')),
    CONSTRAINT ck_supply_reservation_bucket CHECK (
        (reservation_kind = 'NORMAL' AND origin_bucket = 'AVAILABLE')
        OR (reservation_kind = 'REWORK_INPUT' AND origin_bucket = 'REWORK')
    ),
    CONSTRAINT ck_supply_reservation_qty CHECK (
        reserved_qty > 0 AND consumed_qty >= 0 AND released_qty >= 0
        AND consumed_qty + released_qty <= reserved_qty
        AND (status <> 'CLOSED' OR consumed_qty + released_qty = reserved_qty)
    ),
    CONSTRAINT ck_supply_reservation_status CHECK (status IN ('ACTIVE', 'CLOSED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T41 实际供给预留';

-- T42 实际物料投入（追加事实）
CREATE TABLE aps_material_consumption (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reservation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_member_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity DECIMAL(18, 6) NOT NULL,
    quantity_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consumed_at DATETIME(3) NOT NULL,
    reversal_of_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_material_consumption_factory_id (factory_id, id),
    UNIQUE KEY uq_material_consumption_event (factory_id, quantity_event_id),
    UNIQUE KEY uq_material_consumption_reversal (factory_id, reversal_of_id),
    KEY ix_material_consumption_reservation (factory_id, reservation_id, consumed_at),
    KEY ix_material_consumption_member (factory_id, run_member_id, consumed_at),
    CONSTRAINT fk_material_consumption_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_material_consumption_reservation FOREIGN KEY (factory_id, reservation_id) REFERENCES aps_supply_reservation (factory_id, id),
    CONSTRAINT fk_material_consumption_member FOREIGN KEY (factory_id, run_member_id) REFERENCES aps_run_member (factory_id, id),
    CONSTRAINT fk_material_consumption_event FOREIGN KEY (factory_id, quantity_event_id) REFERENCES aps_quantity_event (factory_id, id),
    CONSTRAINT fk_material_consumption_reversal FOREIGN KEY (factory_id, reversal_of_id) REFERENCES aps_material_consumption (factory_id, id),
    CONSTRAINT ck_material_consumption_qty CHECK (quantity > 0),
    CONSTRAINT ck_material_consumption_self CHECK (reversal_of_id IS NULL OR reversal_of_id <> id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T42 实际物料投入';

-- T43 不良处置；inbox_id 的前向外键在文末补加。
CREATE TABLE aps_quality_disposition (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_lot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    disposition_kind VARCHAR(32) NOT NULL,
    quantity DECIMAL(18, 6) NOT NULL,
    requires_replacement BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approved_by BIGINT NULL,
    approved_at DATETIME(3) NULL,
    inbox_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_quality_disposition_factory_id (factory_id, id),
    UNIQUE KEY uq_quality_disposition_command (factory_id, inbox_id, output_lot_id),
    KEY ix_quality_disposition_lot (factory_id, output_lot_id, status),
    CONSTRAINT fk_quality_disposition_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_quality_disposition_lot FOREIGN KEY (factory_id, output_lot_id) REFERENCES aps_output_lot (factory_id, id),
    CONSTRAINT ck_quality_disposition_kind CHECK (disposition_kind IN ('REWORK', 'SCRAP')),
    CONSTRAINT ck_quality_disposition_qty CHECK (quantity > 0),
    CONSTRAINT ck_quality_disposition_replace CHECK (
        requires_replacement IN (0, 1) AND (requires_replacement = 0 OR disposition_kind = 'SCRAP')
    ),
    CONSTRAINT ck_quality_disposition_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'COMPLETED')),
    CONSTRAINT ck_quality_disposition_approval CHECK (
        (status IN ('APPROVED', 'COMPLETED') AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR status IN ('DRAFT', 'REJECTED')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T43 不良处置';

-- T44 统一领域事件与审计（追加事实）；inbox_id 的前向外键在文末补加。
CREATE TABLE aps_domain_event (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_no INT NOT NULL DEFAULT 1,
    event_type VARCHAR(32) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_user_id BIGINT NULL,
    inbox_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurred_at DATETIME(3) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    payload_json JSON NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_domain_event_factory_id (factory_id, id),
    UNIQUE KEY uq_domain_event_command (factory_id, inbox_id, event_no),
    KEY ix_domain_event_entity (factory_id, entity_type, entity_id, occurred_at),
    KEY ix_domain_event_run (factory_id, execution_run_id, occurred_at),
    CONSTRAINT fk_domain_event_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_domain_event_run FOREIGN KEY (factory_id, execution_run_id) REFERENCES aps_execution_run (factory_id, id),
    CONSTRAINT ck_domain_event_no CHECK (event_no > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T44 统一领域事件与审计';

-- T45 命令与外部事件幂等接收
CREATE TABLE aps_inbox (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_event_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    result_json JSON NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    lease_until DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_inbox_factory_id (factory_id, id),
    UNIQUE KEY uq_inbox_idempotency (factory_id, source_system, command_type, external_event_id),
    KEY ix_inbox_worker (factory_id, status, next_retry_at, lease_until),
    CONSTRAINT fk_inbox_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT ck_inbox_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'APPLIED', 'REJECTED', 'RETRY_WAIT')),
    CONSTRAINT ck_inbox_retry CHECK (retry_count >= 0),
    CONSTRAINT ck_inbox_lease CHECK (status <> 'PROCESSING' OR lease_until IS NOT NULL),
    CONSTRAINT ck_inbox_retry_time CHECK (status <> 'RETRY_WAIT' OR next_retry_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T45 命令与外部事件幂等接收';

-- T46 可靠外发与接收确认
CREATE TABLE aps_outbox (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    factory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_system VARCHAR(64) NOT NULL,
    message_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    aggregate_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    aggregate_sequence BIGINT NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NULL,
    lease_until DATETIME(3) NULL,
    acknowledged_at DATETIME(3) NULL,
    remote_receipt VARCHAR(200) NULL,
    last_error VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ruoyi_user_id BIGINT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_factory_id (factory_id, id),
    UNIQUE KEY uq_outbox_message (factory_id, target_system, message_key),
    UNIQUE KEY uq_outbox_aggregate_seq (factory_id, target_system, aggregate_key, aggregate_sequence),
    KEY ix_outbox_worker (factory_id, target_system, status, next_attempt_at, lease_until),
    KEY ix_outbox_plan_version (factory_id, plan_version_id),
    CONSTRAINT fk_outbox_factory FOREIGN KEY (factory_id) REFERENCES aps_factory (id),
    CONSTRAINT fk_outbox_plan_version FOREIGN KEY (factory_id, plan_version_id) REFERENCES aps_plan_version (factory_id, id),
    CONSTRAINT ck_outbox_sequence CHECK (aggregate_sequence >= 0),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'ACKNOWLEDGED', 'RETRY_WAIT', 'DEAD')),
    CONSTRAINT ck_outbox_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_ack CHECK (
        (status = 'ACKNOWLEDGED' AND acknowledged_at IS NOT NULL)
        OR (status <> 'ACKNOWLEDGED' AND acknowledged_at IS NULL)
    ),
    CONSTRAINT ck_outbox_lease CHECK (status <> 'SENDING' OR lease_until IS NOT NULL),
    CONSTRAINT ck_outbox_retry_time CHECK (status <> 'RETRY_WAIT' OR next_attempt_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'T46 可靠外发与接收确认';

-- -----------------------------------------------------------------------------
-- 全部目标表已经存在后，再补前向或循环外键。
-- MySQL 不支持 DEFERRABLE；这些约束不改变服务事务必须执行的归属校验。
-- -----------------------------------------------------------------------------

ALTER TABLE aps_production_lot
    ADD CONSTRAINT fk_production_lot_disposition
        FOREIGN KEY (factory_id, source_disposition_id) REFERENCES aps_quality_disposition (factory_id, id),
    ADD CONSTRAINT fk_production_lot_source_task
        FOREIGN KEY (factory_id, source_task_id) REFERENCES aps_task (factory_id, id),
    ADD CONSTRAINT fk_production_lot_return_task
        FOREIGN KEY (factory_id, return_task_id) REFERENCES aps_task (factory_id, id);

ALTER TABLE aps_task
    ADD CONSTRAINT fk_task_current_spec
        FOREIGN KEY (factory_id, id, current_spec_id) REFERENCES aps_task_spec (factory_id, task_id, id);

ALTER TABLE aps_plan_scope
    ADD CONSTRAINT fk_plan_scope_current_version
        FOREIGN KEY (factory_id, id, current_version_id) REFERENCES aps_plan_version (factory_id, scope_id, id);

ALTER TABLE aps_plan_job
    ADD CONSTRAINT fk_plan_job_carry_run
        FOREIGN KEY (factory_id, carry_run_id) REFERENCES aps_execution_run (factory_id, id);

ALTER TABLE aps_plan_supply
    ADD CONSTRAINT fk_plan_supply_output_lot
        FOREIGN KEY (factory_id, source_output_lot_id) REFERENCES aps_output_lot (factory_id, id);

ALTER TABLE aps_actual_occupancy
    ADD CONSTRAINT fk_actual_occupancy_void_event
        FOREIGN KEY (factory_id, voided_by_event_id) REFERENCES aps_domain_event (factory_id, id);

ALTER TABLE aps_production_report
    ADD CONSTRAINT fk_production_report_inbox
        FOREIGN KEY (factory_id, inbox_id) REFERENCES aps_inbox (factory_id, id);

ALTER TABLE aps_quantity_event
    ADD CONSTRAINT fk_quantity_event_inbox
        FOREIGN KEY (factory_id, inbox_id) REFERENCES aps_inbox (factory_id, id),
    ADD CONSTRAINT fk_quantity_event_reservation
        FOREIGN KEY (factory_id, reservation_id) REFERENCES aps_supply_reservation (factory_id, id);

ALTER TABLE aps_supply_reservation
    ADD CONSTRAINT fk_supply_reservation_inbox
        FOREIGN KEY (factory_id, inbox_id) REFERENCES aps_inbox (factory_id, id);

ALTER TABLE aps_quality_disposition
    ADD CONSTRAINT fk_quality_disposition_inbox
        FOREIGN KEY (factory_id, inbox_id) REFERENCES aps_inbox (factory_id, id);

ALTER TABLE aps_domain_event
    ADD CONSTRAINT fk_domain_event_inbox
        FOREIGN KEY (factory_id, inbox_id) REFERENCES aps_inbox (factory_id, id);

-- 建库后必须由数据库专项验证确认 SHOW CREATE TABLE、外键拒绝、事务、并发和性能；
-- 本文件生成时未连接或修改任何数据库。
