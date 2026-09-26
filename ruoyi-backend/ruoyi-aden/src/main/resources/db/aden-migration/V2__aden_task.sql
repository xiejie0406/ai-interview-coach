-- Aden V2: Task 聚合与步骤。BIGINT 均为 signed；跨线契约序列化为十进制字符串。

create table aden_task (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    task_id char(36) character set ascii collate ascii_bin not null,
    task_type varchar(64) character set ascii collate ascii_bin not null,
    required_capability varchar(96) character set ascii collate ascii_bin not null,
    task_state varchar(32) character set ascii collate ascii_bin not null,
    correlation_id char(36) character set ascii collate ascii_bin not null,
    title varchar(200) not null,
    input_json json not null,
    result_json json null,
    error_code varchar(64) character set ascii collate ascii_bin null,
    error_message varchar(500) null,
    latest_step_no int not null default 0,
    version bigint not null default 0,
    create_idempotency_key varchar(128) character set ascii collate ascii_bin null,
    create_request_hash char(64) character set ascii collate ascii_bin null,
    created_by_ruoyi_user_id bigint not null,
    cancel_requested_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, task_id),
    constraint fk_aden_task_workspace foreign key (workspace_id)
        references aden_workspace (workspace_id) on delete restrict on update restrict,
    constraint ck_aden_task_state check (task_state in (
        'DRAFT', 'VALIDATING', 'QUEUED', 'RUNNING', 'WAITING_USER', 'WAITING_EXTERNAL',
        'CANCEL_REQUESTED', 'SUCCEEDED', 'FAILED', 'CANCELED'
    )),
    constraint ck_aden_task_type check (task_type = 'SYNTHETIC_CORE'),
    constraint ck_aden_task_capability check (required_capability = 'CORE'),
    constraint ck_aden_task_input_size check (octet_length(input_json) <= 65535),
    constraint ck_aden_task_result_size check (result_json is null or octet_length(result_json) <= 65535),
    constraint ck_aden_task_version check (version >= 0),
    constraint ck_aden_task_step_no check (latest_step_no >= 0),
    constraint ck_aden_task_create_idempotency check (
        (create_idempotency_key is null and create_request_hash is null)
        or (create_idempotency_key is not null and create_request_hash is not null)
    ),
    unique key uq_aden_task_create_idem (workspace_id, create_idempotency_key),
    index ix_aden_task_workspace_state (workspace_id, task_state, updated_at, task_id),
    index ix_aden_task_workspace_creator (workspace_id, created_by_ruoyi_user_id, created_at),
    index ix_aden_task_workspace_capability (workspace_id, required_capability, task_state, created_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Aden authoritative task aggregate';

create table aden_task_step (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    step_id char(36) character set ascii collate ascii_bin not null,
    task_id char(36) character set ascii collate ascii_bin not null,
    step_no int not null,
    attempt_no int not null default 1,
    step_type varchar(64) character set ascii collate ascii_bin not null,
    step_state varchar(32) character set ascii collate ascii_bin not null,
    input_json json not null,
    output_json json null,
    error_code varchar(64) character set ascii collate ascii_bin null,
    error_message varchar(500) null,
    version bigint not null default 0,
    started_at datetime(6) null,
    finished_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, step_id),
    constraint fk_aden_step_task foreign key (workspace_id, task_id)
        references aden_task (workspace_id, task_id) on delete restrict on update restrict,
    constraint ck_aden_step_state check (step_state in (
        'PENDING', 'READY', 'RUNNING', 'WAITING_RETRY', 'SUCCEEDED', 'FAILED',
        'CANCELED', 'OUTCOME_UNKNOWN'
    )),
    constraint ck_aden_step_numbers check (step_no > 0 and attempt_no > 0),
    constraint ck_aden_step_input_size check (octet_length(input_json) <= 65535),
    constraint ck_aden_step_output_size check (output_json is null or octet_length(output_json) <= 65535),
    constraint ck_aden_step_version check (version >= 0),
    unique key uq_aden_step_attempt (workspace_id, task_id, step_no, attempt_no),
    index ix_aden_step_task_state (workspace_id, task_id, step_state, step_no),
    index ix_aden_step_workspace_state (workspace_id, step_state, updated_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Aden task execution step and attempt';
