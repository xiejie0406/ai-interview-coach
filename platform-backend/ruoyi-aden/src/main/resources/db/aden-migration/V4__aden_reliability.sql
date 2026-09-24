-- Aden V4: 有序事件、Outbox、Inbox 幂等事实和审计账本。

create table aden_event (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    event_id char(36) character set ascii collate ascii_bin not null,
    event_seq bigint not null,
    aggregate_type varchar(32) character set ascii collate ascii_bin not null,
    aggregate_id char(36) character set ascii collate ascii_bin not null,
    aggregate_version bigint not null,
    event_type varchar(96) character set ascii collate ascii_bin not null,
    payload_json json not null,
    correlation_id char(36) character set ascii collate ascii_bin not null,
    actor_type varchar(24) character set ascii collate ascii_bin not null,
    actor_id varchar(128) character set ascii collate ascii_bin not null,
    occurred_at datetime(6) not null,
    created_at datetime(6) not null,
    primary key (workspace_id, event_id),
    constraint fk_aden_event_workspace foreign key (workspace_id)
        references aden_workspace (workspace_id) on delete restrict on update restrict,
    constraint ck_aden_event_seq check (event_seq > 0 and aggregate_version > 0),
    constraint ck_aden_event_actor check (actor_type in ('OPERATOR', 'RUNNER', 'SYSTEM')),
    constraint ck_aden_event_payload_size check (octet_length(payload_json) <= 65535),
    unique key uq_aden_event_sequence (workspace_id, event_seq),
    unique key uq_aden_event_aggregate_version (workspace_id, aggregate_type, aggregate_id, aggregate_version),
    index ix_aden_event_replay (workspace_id, event_seq, occurred_at),
    index ix_aden_event_correlation (workspace_id, correlation_id, event_seq)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Immutable workspace-ordered domain event';

create table aden_outbox (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    outbox_id char(36) character set ascii collate ascii_bin not null,
    event_id char(36) character set ascii collate ascii_bin not null,
    consumer varchar(48) character set ascii collate ascii_bin not null,
    outbox_state varchar(16) character set ascii collate ascii_bin not null,
    attempts int not null default 0,
    available_at datetime(6) not null,
    claim_token char(36) character set ascii collate ascii_bin null,
    claimed_until datetime(6) null,
    last_error varchar(500) null,
    created_at datetime(6) not null,
    published_at datetime(6) null,
    updated_at datetime(6) not null,
    primary key (workspace_id, outbox_id),
    constraint fk_aden_outbox_event foreign key (workspace_id, event_id)
        references aden_event (workspace_id, event_id) on delete restrict on update restrict,
    constraint ck_aden_outbox_state check (outbox_state in ('PENDING', 'CLAIMED', 'PUBLISHED', 'DEAD')),
    constraint ck_aden_outbox_attempts check (attempts >= 0),
    constraint ck_aden_outbox_claim check (
        (claim_token is null and claimed_until is null)
        or (claim_token is not null and claimed_until is not null)
    ),
    unique key uq_aden_outbox_event_consumer (workspace_id, event_id, consumer),
    index ix_aden_outbox_claim (workspace_id, outbox_state, available_at, claimed_until),
    index ix_aden_outbox_global_claim (outbox_state, available_at, outbox_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Reliable event publication outbox';

create table aden_inbox (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    inbox_id char(36) character set ascii collate ascii_bin not null,
    consumer varchar(48) character set ascii collate ascii_bin not null,
    producer_id varchar(128) character set ascii collate ascii_bin not null,
    operation varchar(64) character set ascii collate ascii_bin not null,
    idempotency_key varchar(128) character set ascii collate ascii_bin not null,
    request_hash char(64) character set ascii collate ascii_bin not null,
    inbox_state varchar(16) character set ascii collate ascii_bin not null,
    response_status int null,
    response_json json null,
    in_progress_until datetime(6) null,
    retention_until datetime(6) not null,
    created_at datetime(6) not null,
    completed_at datetime(6) null,
    updated_at datetime(6) not null,
    primary key (workspace_id, inbox_id),
    constraint fk_aden_inbox_workspace foreign key (workspace_id)
        references aden_workspace (workspace_id) on delete restrict on update restrict,
    constraint ck_aden_inbox_state check (inbox_state in ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    constraint ck_aden_inbox_response_size check (response_json is null or octet_length(response_json) <= 65535),
    constraint ck_aden_inbox_response_status check (
        response_status is null or (response_status between 100 and 599)
    ),
    unique key uq_aden_inbox_idempotency (workspace_id, consumer, producer_id, operation, idempotency_key),
    index ix_aden_inbox_retention (workspace_id, retention_until),
    index ix_aden_inbox_progress (workspace_id, inbox_state, in_progress_until)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Idempotent command and receipt fact';

create table aden_audit_event (
    workspace_id char(36) character set ascii collate ascii_bin null,
    audit_event_id char(36) character set ascii collate ascii_bin not null,
    scope_type varchar(16) character set ascii collate ascii_bin not null,
    action_code varchar(96) character set ascii collate ascii_bin not null,
    resource_type varchar(48) character set ascii collate ascii_bin not null,
    resource_id varchar(128) character set ascii collate ascii_bin not null,
    actor_type varchar(24) character set ascii collate ascii_bin not null,
    actor_id varchar(128) character set ascii collate ascii_bin not null,
    correlation_id char(36) character set ascii collate ascii_bin not null,
    outcome varchar(16) character set ascii collate ascii_bin not null,
    details_json json not null,
    occurred_at datetime(6) not null,
    created_at datetime(6) not null,
    primary key (audit_event_id),
    constraint fk_aden_audit_workspace foreign key (workspace_id)
        references aden_workspace (workspace_id) on delete restrict on update restrict,
    constraint ck_aden_audit_actor check (actor_type in ('OPERATOR', 'RUNNER', 'SYSTEM')),
    constraint ck_aden_audit_outcome check (outcome in ('SUCCEEDED', 'REJECTED', 'FAILED')),
    constraint ck_aden_audit_scope check (
        (scope_type = 'PLATFORM' and workspace_id is null)
        or (scope_type = 'WORKSPACE' and workspace_id is not null)
    ),
    constraint ck_aden_audit_details_size check (octet_length(details_json) <= 32768),
    unique key uq_aden_audit_workspace_id (workspace_id, audit_event_id),
    index ix_aden_audit_time (scope_type, workspace_id, occurred_at, audit_event_id),
    index ix_aden_audit_resource (workspace_id, resource_type, resource_id, occurred_at),
    index ix_aden_audit_correlation (workspace_id, correlation_id, occurred_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Append-only Aden audit event';
