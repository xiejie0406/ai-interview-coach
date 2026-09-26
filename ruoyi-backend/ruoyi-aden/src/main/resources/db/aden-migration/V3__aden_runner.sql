-- Aden V3: Runner 注册、凭据、会话和投递租约。

create table aden_runner (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    runner_id char(36) character set ascii collate ascii_bin not null,
    runner_name varchar(80) not null,
    runner_presence varchar(24) character set ascii collate ascii_bin not null,
    capabilities_json json not null,
    current_session_epoch bigint not null default 0,
    current_credential_epoch bigint not null default 0,
    version bigint not null default 0,
    last_seen_at datetime(6) null,
    created_by_ruoyi_user_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, runner_id),
    constraint fk_aden_runner_workspace foreign key (workspace_id)
        references aden_workspace (workspace_id) on delete restrict on update restrict,
    constraint ck_aden_runner_presence check (runner_presence in ('ONLINE', 'OFFLINE', 'LEASED', 'QUARANTINED')),
    constraint ck_aden_runner_capabilities check (
        json_type(capabilities_json) = 'ARRAY' and octet_length(capabilities_json) <= 32768
    ),
    constraint ck_aden_runner_version check (version >= 0),
    constraint ck_aden_runner_epochs check (current_session_epoch >= 0 and current_credential_epoch >= 0),
    unique key uq_aden_runner_name (workspace_id, runner_name),
    index ix_aden_runner_presence (workspace_id, runner_presence, last_seen_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Registered local execution runner';

create table aden_runner_credential (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    credential_id char(36) character set ascii collate ascii_bin not null,
    runner_id char(36) character set ascii collate ascii_bin not null,
    credential_epoch bigint not null,
    credential_keyed_digest char(64) character set ascii collate ascii_bin not null,
    pepper_key_id varchar(64) character set ascii collate ascii_bin not null,
    credential_status varchar(16) character set ascii collate ascii_bin not null,
    version bigint not null default 0,
    issued_at datetime(6) not null,
    expires_at datetime(6) not null,
    revoked_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, credential_id),
    constraint fk_aden_credential_runner foreign key (workspace_id, runner_id)
        references aden_runner (workspace_id, runner_id) on delete restrict on update restrict,
    constraint ck_aden_credential_status check (credential_status in ('ACTIVE', 'EXPIRED', 'REVOKED')),
    constraint ck_aden_credential_version check (version >= 0),
    constraint ck_aden_credential_epoch check (credential_epoch > 0),
    constraint ck_aden_credential_expiry check (expires_at > issued_at),
    unique key uq_aden_credential_epoch (workspace_id, runner_id, credential_epoch),
    unique key uq_aden_credential_digest (workspace_id, credential_keyed_digest),
    index ix_aden_credential_public_id (credential_id),
    index ix_aden_credential_runner (workspace_id, runner_id, credential_status, expires_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Hashed runner credential metadata';

create table aden_runner_session (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    session_id char(36) character set ascii collate ascii_bin not null,
    runner_id char(36) character set ascii collate ascii_bin not null,
    credential_id char(36) character set ascii collate ascii_bin not null,
    session_status varchar(16) character set ascii collate ascii_bin not null,
    session_epoch bigint not null,
    session_keyed_digest char(64) character set ascii collate ascii_bin not null,
    pepper_key_id varchar(64) character set ascii collate ascii_bin not null,
    capacity int not null,
    in_flight int not null default 0,
    heartbeat_sequence bigint not null default 0,
    metadata_json json not null,
    version bigint not null default 0,
    heartbeat_at datetime(6) not null,
    expires_at datetime(6) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, session_id),
    constraint fk_aden_session_runner foreign key (workspace_id, runner_id)
        references aden_runner (workspace_id, runner_id) on delete restrict on update restrict,
    constraint fk_aden_session_credential foreign key (workspace_id, credential_id)
        references aden_runner_credential (workspace_id, credential_id) on delete restrict on update restrict,
    constraint ck_aden_session_status check (session_status in ('ACTIVE', 'EXPIRED', 'REVOKED', 'SUPERSEDED', 'CLOSED')),
    constraint ck_aden_session_capacity check (capacity > 0 and in_flight >= 0 and in_flight <= capacity),
    constraint ck_aden_session_sequences check (session_epoch > 0 and heartbeat_sequence >= 0),
    constraint ck_aden_session_metadata_size check (octet_length(metadata_json) <= 16384),
    constraint ck_aden_session_version check (version >= 0),
    constraint ck_aden_session_expiry check (expires_at > heartbeat_at),
    unique key uq_aden_session_epoch (workspace_id, runner_id, session_epoch),
    unique key uq_aden_session_digest (workspace_id, session_keyed_digest),
    index ix_aden_session_public_id (session_id),
    index ix_aden_session_runner (workspace_id, runner_id, session_status, expires_at),
    index ix_aden_session_claimable (workspace_id, session_status, expires_at, in_flight)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Authenticated runner session with capacity';

create table aden_runner_delivery (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    delivery_id char(36) character set ascii collate ascii_bin not null,
    task_id char(36) character set ascii collate ascii_bin not null,
    step_id char(36) character set ascii collate ascii_bin not null,
    owner_session_id char(36) character set ascii collate ascii_bin null,
    delivery_state varchar(32) character set ascii collate ascii_bin not null,
    attempt_no int not null,
    required_capability varchar(96) character set ascii collate ascii_bin not null,
    priority int not null default 100,
    available_at datetime(6) not null,
    task_package_json json not null,
    task_package_hash char(64) character set ascii collate ascii_bin not null,
    owner_session_epoch bigint null,
    fence_token bigint not null default 0,
    latest_receipt_sequence bigint not null default 0,
    heartbeat_sequence bigint not null default 0,
    lease_until datetime(6) null,
    cancel_requested_at datetime(6) null,
    started_at datetime(6) null,
    completed_at datetime(6) null,
    outcome_json json null,
    error_code varchar(64) character set ascii collate ascii_bin null,
    error_message varchar(500) null,
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, delivery_id),
    constraint fk_aden_delivery_task foreign key (workspace_id, task_id)
        references aden_task (workspace_id, task_id) on delete restrict on update restrict,
    constraint fk_aden_delivery_step foreign key (workspace_id, step_id)
        references aden_task_step (workspace_id, step_id) on delete restrict on update restrict,
    constraint fk_aden_delivery_session foreign key (workspace_id, owner_session_id)
        references aden_runner_session (workspace_id, session_id) on delete restrict on update restrict,
    constraint ck_aden_delivery_state check (delivery_state in (
        'READY', 'LEASED', 'RUNNING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_FINAL',
        'CANCELED', 'OUTCOME_UNKNOWN'
    )),
    constraint ck_aden_delivery_attempt check (
        attempt_no between 1 and 100 and priority between 0 and 1000
        and fence_token >= 0 and latest_receipt_sequence >= 0 and heartbeat_sequence >= 0
    ),
    constraint ck_aden_delivery_capability check (required_capability = 'CORE'),
    constraint ck_aden_delivery_package check (octet_length(task_package_json) <= 65535),
    constraint ck_aden_delivery_owner check (
        ((owner_session_id is null and owner_session_epoch is null)
            or (owner_session_id is not null and owner_session_epoch is not null))
        and (delivery_state <> 'READY'
            or (owner_session_id is null and owner_session_epoch is null and lease_until is null))
        and (delivery_state not in ('LEASED', 'RUNNING')
            or (owner_session_id is not null and owner_session_epoch is not null and lease_until is not null))
    ),
    constraint ck_aden_delivery_outcome_size check (outcome_json is null or octet_length(outcome_json) <= 65535),
    constraint ck_aden_delivery_version check (version >= 0),
    unique key uq_aden_delivery_attempt (workspace_id, task_id, step_id, attempt_no),
    index ix_aden_delivery_claim (workspace_id, delivery_state, available_at, required_capability, priority, delivery_id),
    index ix_aden_delivery_session_lease (workspace_id, owner_session_id, delivery_state, lease_until),
    index ix_aden_delivery_task_state (workspace_id, task_id, delivery_state, updated_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Fenced task-step delivery lease';
