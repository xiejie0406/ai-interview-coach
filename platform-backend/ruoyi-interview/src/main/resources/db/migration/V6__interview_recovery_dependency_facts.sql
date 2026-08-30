-- 后续兼容迁移：Interview 恢复投影可达的最小业务事实。
-- 这些表不承载 RuoYi 账号/RBAC；用户键保持业务逻辑引用，且不建立跨库物理外键。
create schema if not exists catalog;
create schema if not exists practice;
create schema if not exists billing;
create schema if not exists evaluation;
create schema if not exists learning;

create table if not exists catalog.question (
    tenant_id varchar(128) not null,
    question_id varchar(128) not null,
    stable_key varchar(128) not null,
    status varchar(32) not null default 'PUBLISHED',
    aggregate_version bigint not null default 0,
    primary key (tenant_id, question_id),
    unique (tenant_id, stable_key)
);
create table if not exists catalog.question_version (
    tenant_id varchar(128) not null,
    question_version_id varchar(128) not null,
    question_id varchar(128) not null,
    version_no integer not null,
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null default 'disabled',
    body_algorithm varchar(64) not null default 'AES-256-GCM',
    body_nonce bytea not null default decode('', 'hex'),
    body_ciphertext bytea not null default decode('', 'hex'),
    body_aad_hash varchar(128) not null default '',
    difficulty varchar(64) not null default 'UNKNOWN',
    target_roles jsonb not null default '[]'::jsonb,
    locale varchar(35) not null default 'zh-CN',
    source_id varchar(128), source_version_id varchar(128), source_version_no integer,
    source_content_hash varchar(128), source_license_code varchar(128),
    source_verification_fact_id varchar(128), source_verified_at timestamptz,
    authored_by varchar(128) not null default 'system', reviewed_by varchar(128),
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, question_version_id),
    unique (tenant_id, question_id, version_no),
    unique (tenant_id, question_version_id, version_no, content_hash)
);
create table if not exists catalog.rubric_version (
    tenant_id varchar(128) not null,
    rubric_version_id varchar(128) not null,
    question_version_id varchar(128) not null,
    version_no integer not null,
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null default 'disabled',
    body_algorithm varchar(64) not null default 'AES-256-GCM',
    body_nonce bytea not null default decode('', 'hex'),
    body_ciphertext bytea not null default decode('', 'hex'),
    body_aad_hash varchar(128) not null default '',
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, rubric_version_id),
    unique (tenant_id, rubric_version_id, version_no, content_hash, question_version_id)
);
create table if not exists catalog.question_publication (
    tenant_id varchar(128) not null, publication_id varchar(128) not null,
    question_id varchar(128) not null, question_version_id varchar(128) not null,
    question_version_no integer not null, question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null, rubric_version_no integer not null,
    rubric_content_hash varchar(128) not null, source_verification_fact_id varchar(128) not null default 'pending',
    reviewed_by varchar(128) not null default 'system', reason_code varchar(64) not null default 'INITIAL',
    published_at timestamptz not null default current_timestamp,
    primary key (tenant_id, publication_id), unique (tenant_id, question_version_id)
);
create table if not exists catalog.user_question_answer (
    catalog_tenant_id varchar(128) not null, owner_tenant_id varchar(128) not null,
    user_id varchar(128) not null, question_id varchar(128) not null,
    answer_key_id varchar(255) not null, answer_algorithm varchar(64) not null,
    answer_nonce bytea not null, answer_ciphertext bytea not null, answer_aad_hash varchar(128) not null,
    created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
    primary key (catalog_tenant_id, owner_tenant_id, user_id, question_id)
);
create table if not exists catalog.public_question_creator (
    catalog_tenant_id varchar(128) not null, question_id varchar(128) not null,
    creator_ruoyi_user_id bigint not null, created_at timestamptz not null default current_timestamp,
    primary key (catalog_tenant_id, question_id)
);

create table if not exists practice.attempt (
    tenant_id varchar(128) not null, attempt_id varchar(128) not null, user_id varchar(128) not null,
    question_version_id varchar(128) not null, question_version_no integer not null, question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null, rubric_version_no integer not null, rubric_content_hash varchar(128) not null,
    state varchar(16) not null default 'DRAFT', started_at timestamptz not null default current_timestamp,
    submitted_at timestamptz, cancelled_at timestamptz, draft_content_hash varchar(128), draft_saved_at timestamptz,
    draft_key_id varchar(255), draft_algorithm varchar(64), draft_nonce bytea, draft_ciphertext bytea, draft_aad_hash varchar(128),
    aggregate_version bigint not null default 0, primary key (tenant_id, attempt_id)
);
create table if not exists practice.answer_version (
    tenant_id varchar(128) not null, answer_version_id varchar(128) not null, attempt_id varchar(128) not null,
    version_no integer not null, source varchar(32) not null, content_hash varchar(128) not null,
    body_key_id varchar(255) not null, body_algorithm varchar(64) not null, body_nonce bytea not null,
    body_ciphertext bytea not null, body_aad_hash varchar(128) not null, submitted_by varchar(128) not null,
    submitted_at timestamptz not null default current_timestamp, supersedes_id varchar(128),
    primary key (tenant_id, answer_version_id), unique (tenant_id, attempt_id, version_no)
);

create table if not exists billing.entitlement (
    tenant_id varchar(128) not null, entitlement_id varchar(128) not null, user_id varchar(128) not null,
    product_plan_id varchar(128) not null, source varchar(32) not null, valid_from timestamptz not null,
    valid_to timestamptz not null, usage_unit varchar(64) not null, limit_value numeric(38,8) not null default 0,
    consumed_value numeric(38,8) not null default 0, reserved_value numeric(38,8) not null default 0,
    state varchar(16) not null default 'ACTIVE', aggregate_version bigint not null default 0,
    primary key (tenant_id, entitlement_id), unique (tenant_id, entitlement_id, user_id, usage_unit)
);
create table if not exists billing.usage_reservation (
    tenant_id varchar(128) not null, reservation_id varchar(128) not null, user_id varchar(128) not null,
    entitlement_id varchar(128) not null, business_operation_id varchar(128) not null,
    idempotency_key varchar(256) not null, usage_unit varchar(64) not null, reserved_value numeric(38,8) not null default 0,
    expires_at timestamptz not null default current_timestamp, state varchar(16) not null default 'RESERVED',
    settled_value numeric(38,8), release_reason_code varchar(64), aggregate_version bigint not null default 0,
    primary key (tenant_id, reservation_id), unique (tenant_id, business_operation_id, usage_unit),
    unique (tenant_id, user_id, idempotency_key)
);
create table if not exists billing.usage_settlement (
    tenant_id varchar(128) not null, settlement_id varchar(128) not null, user_id varchar(128) not null,
    reservation_id varchar(128) not null, business_operation_id varchar(128) not null, usage_unit varchar(64) not null,
    reserved_value numeric(38,8) not null default 0, settled_value numeric(38,8) not null default 0,
    released_value numeric(38,8) not null default 0, rule_version varchar(128) not null default 'v1',
    settled_at timestamptz not null default current_timestamp, primary key (tenant_id, settlement_id),
    unique (tenant_id, reservation_id)
);

create table if not exists platform.job (
    tenant_id varchar(128) not null, job_id varchar(128) not null, job_type varchar(128) not null,
    business_operation_id varchar(128) not null, payload_references jsonb not null default '{}'::jsonb,
    max_attempts integer not null default 3, state varchar(32) not null default 'PENDING', available_at timestamptz not null default current_timestamp,
    lease_owner varchar(128), lease_expires_at timestamptz, heartbeat_at timestamptz, attempt_count integer not null default 0,
    last_error_code varchar(128), aggregate_version bigint not null default 0,
    primary key (tenant_id, job_id), unique (tenant_id, job_type, business_operation_id)
);
create table if not exists platform.outbox_event (
    tenant_id varchar(128) not null, event_id varchar(128) not null, aggregate_type varchar(128) not null,
    aggregate_id varchar(128) not null, source_aggregate_version bigint not null default 0, event_type varchar(160) not null,
    schema_version integer not null default 1, correlation_id varchar(128) not null, payload_references jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default current_timestamp, primary key (tenant_id, event_id),
    unique (tenant_id, aggregate_type, aggregate_id, source_aggregate_version, event_type)
);
create table if not exists platform.outbox_delivery (
    tenant_id varchar(128) not null, event_id varchar(128) not null, state varchar(32) not null default 'PENDING',
    available_at timestamptz not null default current_timestamp, claimed_by varchar(128), claim_expires_at timestamptz,
    published_at timestamptz, attempt_count integer not null default 0, last_error_code varchar(128), aggregate_version bigint not null default 0,
    primary key (tenant_id, event_id)
);
create table if not exists platform.idempotency_record (
    tenant_id varchar(128) not null, principal_ref_hash varchar(128) not null, operation varchar(160) not null,
    idempotency_key varchar(128) not null, request_hash varchar(128) not null, expires_at timestamptz not null,
    state varchar(32) not null, resource_references jsonb not null default '{}'::jsonb, response_status integer,
    error_code varchar(128), primary key (tenant_id, principal_ref_hash, operation, idempotency_key)
);

create table if not exists evaluation.evaluation_run (
    tenant_id varchar(128) not null, evaluation_id varchar(128) not null, user_id varchar(128) not null,
    source_interview_id varchar(128) not null, status varchar(32) not null default 'PENDING',
    requested_at timestamptz not null default current_timestamp, report_id varchar(128),
    primary key (tenant_id, evaluation_id), unique (tenant_id, evaluation_id, user_id, source_interview_id)
);
create table if not exists evaluation.report (
    tenant_id varchar(128) not null, report_id varchar(128) not null, user_id varchar(128) not null,
    evaluation_id varchar(128) not null, source_interview_id varchar(128) not null, status varchar(16) not null default 'PENDING',
    current_report_version_id varchar(128), failure_code varchar(128), aggregate_version bigint not null default 0,
    primary key (tenant_id, report_id), unique (tenant_id, evaluation_id), unique (tenant_id, source_interview_id)
);
create table if not exists evaluation.report_version (
    tenant_id varchar(128) not null, report_version_id varchar(128) not null, report_id varchar(128) not null,
    evaluation_id varchar(128) not null, evaluation_version_id varchar(128) not null, created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, report_version_id)
);
create table if not exists evaluation.evidence_bundle (tenant_id varchar(128) not null, evidence_bundle_id varchar(128) not null, answer_version_id varchar(128) not null, answer_hash varchar(128) not null, prompt_key varchar(160) not null, prompt_version integer not null, schema_key varchar(160) not null, schema_version integer not null, primary key (tenant_id, evidence_bundle_id));
create table if not exists evaluation.evidence_item (tenant_id varchar(128) not null, evidence_item_id varchar(128) not null, evidence_bundle_id varchar(128) not null, primary key (tenant_id, evidence_item_id));
create table if not exists evaluation.rubric_judgement (tenant_id varchar(128) not null, judgement_id varchar(128) not null, rubric_version_id varchar(128) not null, primary key (tenant_id, judgement_id));
create table if not exists evaluation.rubric_dimension (tenant_id varchar(128) not null, rubric_dimension_id varchar(128) not null, primary key (tenant_id, rubric_dimension_id));
create table if not exists evaluation.report_feedback (tenant_id varchar(128) not null, feedback_id varchar(128) not null, report_id varchar(128) not null, author_user_id varchar(128) not null, primary key (tenant_id, feedback_id));
create table if not exists evaluation.feedback_content (tenant_id varchar(128) not null, feedback_id varchar(128) not null, content_ref varchar(256) not null, primary key (tenant_id, feedback_id));

create table if not exists learning.plan (
    tenant_id varchar(128) not null, learning_plan_id varchar(128) not null, user_id varchar(128) not null,
    source_report_id varchar(128) not null, source_report_version_id varchar(128) not null, status varchar(16) not null default 'CANDIDATE',
    created_at timestamptz not null default current_timestamp, aggregate_version bigint not null default 0,
    primary key (tenant_id, learning_plan_id)
);
create table if not exists learning.item (
    tenant_id varchar(128) not null, learning_plan_id varchar(128) not null, learning_item_id varchar(128) not null,
    position integer not null, question_version_id varchar(128) not null, status varchar(16) not null default 'PENDING',
    primary key (tenant_id, learning_plan_id, learning_item_id)
);
