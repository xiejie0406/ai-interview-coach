-- Voice 垂直闭环所需的最小 PostgreSQL 前置事实。
-- 账号、密码、角色、权限和 session 仍只属于 MySQL RuoYi；这里仅保存 BIGINT 逻辑引用。
create schema if not exists governance;
create schema if not exists interview;
create schema if not exists voice;

alter table platform.ruoyi_user_binding
    add constraint ruoyi_user_binding_tenant_fk
    foreign key (business_tenant_id) references platform.business_tenant(tenant_id);

alter table platform.business_membership
    add constraint business_membership_tenant_fk
    foreign key (tenant_id) references platform.business_tenant(tenant_id);

alter table platform.profile_version
    add constraint profile_version_owner_content_uq
    unique (business_tenant_id, ruoyi_user_id, profile_version_id, version_no, content_hash);

create or replace function platform.reject_immutable_row_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'immutable row cannot be updated or deleted' using errcode = '55000';
end;
$$;

create table governance.consent_policy_version (
    tenant_id varchar(128) not null,
    policy_version_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    content_hash varchar(128) not null,
    purpose varchar(32) not null check (
        purpose in ('SERVICE_TERMS','PRIVACY_NOTICE','VOICE_CAPTURE','MODEL_PROCESSING')),
    effective_from timestamptz not null,
    primary key (tenant_id, policy_version_id),
    unique (tenant_id, policy_version_id, version_no, content_hash),
    foreign key (tenant_id) references platform.business_tenant(tenant_id)
);

create table governance.consent_record (
    tenant_id varchar(128) not null,
    consent_record_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    policy_version_id varchar(128) not null,
    policy_version_no integer not null,
    policy_content_hash varchar(128) not null,
    purpose varchar(32) not null check (
        purpose in ('SERVICE_TERMS','PRIVACY_NOTICE','VOICE_CAPTURE','MODEL_PROCESSING')),
    action varchar(16) not null check (action in ('GRANTED','REVOKED')),
    source varchar(64) not null,
    effective_at timestamptz not null,
    supersedes_id varchar(128),
    primary key (tenant_id, consent_record_id),
    foreign key (tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    foreign key (tenant_id, policy_version_id, policy_version_no, policy_content_hash)
        references governance.consent_policy_version(
            tenant_id, policy_version_id, version_no, content_hash),
    foreign key (tenant_id, supersedes_id)
        references governance.consent_record(tenant_id, consent_record_id),
    check (supersedes_id is null or supersedes_id <> consent_record_id),
    check (action <> 'REVOKED' or supersedes_id is not null)
);

create index consent_history_idx on governance.consent_record (
    tenant_id, ruoyi_user_id, purpose, effective_at, consent_record_id);

create trigger consent_policy_version_immutable
before update or delete on governance.consent_policy_version
for each row execute function platform.reject_immutable_row_mutation();

create trigger consent_record_immutable
before update or delete on governance.consent_record
for each row execute function platform.reject_immutable_row_mutation();

create table interview.plan (
    tenant_id varchar(128) not null,
    plan_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    profile_version_id varchar(128) not null,
    profile_version_no integer not null,
    profile_content_hash varchar(128) not null,
    mode varchar(16) not null check (mode in ('TEXT','VOICE')),
    expires_at timestamptz not null,
    total_time_budget_millis bigint not null check (total_time_budget_millis > 0),
    total_follow_up_budget integer not null check (total_follow_up_budget >= 0),
    estimate_unit varchar(64) not null,
    estimate_value numeric(38,8) not null check (estimate_value >= 0),
    estimate_rule_version varchar(128) not null,
    content_hash varchar(128) not null,
    plan_version_no integer not null check (plan_version_no > 0),
    state varchar(16) not null check (state in ('DRAFT','CONFIRMED','CANCELLED','EXPIRED')),
    usage_reservation_id varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, plan_id),
    unique (tenant_id, plan_id, plan_version_no),
    unique (tenant_id, plan_id, plan_version_no, content_hash),
    unique (tenant_id, plan_id, plan_version_no, ruoyi_user_id),
    foreign key (tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    foreign key (
        tenant_id, ruoyi_user_id, profile_version_id, profile_version_no, profile_content_hash)
        references platform.profile_version(
            business_tenant_id, ruoyi_user_id, profile_version_id, version_no, content_hash),
    check (state <> 'CONFIRMED' or usage_reservation_id is not null),
    check (state not in ('DRAFT','EXPIRED') or usage_reservation_id is null)
);

create table interview.plan_question (
    tenant_id varchar(128) not null,
    plan_id varchar(128) not null,
    position integer not null check (position > 0),
    question_version_id varchar(128) not null,
    question_version_no integer not null,
    question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null,
    rubric_version_no integer not null,
    rubric_content_hash varchar(128) not null,
    topic_code varchar(128) not null,
    time_budget_millis bigint not null check (time_budget_millis > 0),
    follow_up_budget integer not null check (follow_up_budget >= 0),
    primary key (tenant_id, plan_id, position),
    unique (tenant_id, plan_id, question_version_id),
    foreign key (tenant_id, plan_id)
        references interview.plan(tenant_id, plan_id) on delete cascade
);

create table interview.session (
    tenant_id varchar(128) not null,
    session_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    plan_id varchar(128) not null,
    plan_version_no integer not null,
    plan_content_hash varchar(128) not null,
    usage_reservation_id varchar(128) not null,
    plan_follow_up_budget integer not null check (plan_follow_up_budget >= 0),
    mode varchar(16) not null check (mode in ('TEXT','VOICE')),
    state varchar(32) not null check (state in (
        'READY','IN_PROGRESS','PAUSED','FAILED_RECOVERABLE','COMPLETING',
        'COMPLETED','CANCELLED','FAILED_FINAL')),
    last_stable_sequence integer not null check (last_stable_sequence >= 0),
    started_at timestamptz,
    paused_at timestamptz,
    completing_at timestamptz,
    completed_at timestamptz,
    recovery_expires_at timestamptz,
    failure_code varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, session_id),
    unique (tenant_id, plan_id, plan_version_no),
    unique (tenant_id, session_id, ruoyi_user_id),
    foreign key (tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    foreign key (tenant_id, plan_id, plan_version_no, plan_content_hash)
        references interview.plan(tenant_id, plan_id, plan_version_no, content_hash),
    check ((state = 'PAUSED') = (paused_at is not null)),
    check ((state = 'COMPLETED') = (completed_at is not null)),
    check ((state in ('FAILED_RECOVERABLE','FAILED_FINAL')) = (failure_code is not null)),
    check ((state in ('FAILED_RECOVERABLE','FAILED_FINAL')) = (recovery_expires_at is not null))
);

create table interview.session_plan_question (
    tenant_id varchar(128) not null,
    session_id varchar(128) not null,
    position integer not null check (position > 0),
    question_version_id varchar(128) not null,
    question_version_no integer not null,
    question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null,
    rubric_version_no integer not null,
    rubric_content_hash varchar(128) not null,
    topic_code varchar(128) not null,
    time_budget_millis bigint not null check (time_budget_millis > 0),
    follow_up_budget integer not null check (follow_up_budget >= 0),
    primary key (tenant_id, session_id, position),
    unique (tenant_id, session_id, question_version_id),
    unique (
        tenant_id, session_id, position,
        question_version_id, question_version_no, question_content_hash,
        rubric_version_id, rubric_version_no, rubric_content_hash,
        topic_code, time_budget_millis, follow_up_budget),
    foreign key (tenant_id, session_id)
        references interview.session(tenant_id, session_id)
);

create table interview.turn (
    tenant_id varchar(128) not null,
    session_id varchar(128) not null,
    turn_id varchar(128) not null,
    sequence_no integer not null check (sequence_no > 0),
    planned_position integer not null check (planned_position > 0),
    question_version_id varchar(128) not null,
    question_version_no integer not null,
    question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null,
    rubric_version_no integer not null,
    rubric_content_hash varchar(128) not null,
    topic_code varchar(128) not null,
    time_budget_millis bigint not null check (time_budget_millis > 0),
    follow_up_budget integer not null check (follow_up_budget >= 0),
    parent_turn_id varchar(128),
    kind varchar(16) not null check (kind in ('PRIMARY','FOLLOW_UP','CLARIFICATION')),
    state varchar(32) not null check (state in (
        'PLANNED','QUESTION_COMMITTED','ANSWER_CONFIRMED','CLOSED','SKIPPED','CANCELLED','FAILED')),
    prompt_content_hash varchar(128),
    prompt_key_id varchar(255),
    prompt_algorithm varchar(64),
    prompt_nonce bytea,
    prompt_ciphertext bytea,
    prompt_aad_hash varchar(128),
    prompt_ref_id varchar(128),
    prompt_ref_version_no integer,
    prompt_ref_content_hash varchar(128),
    schema_ref_id varchar(128),
    schema_ref_version_no integer,
    schema_ref_content_hash varchar(128),
    committed_at timestamptz,
    primary key (tenant_id, session_id, turn_id),
    unique (tenant_id, session_id, sequence_no),
    foreign key (tenant_id, session_id)
        references interview.session(tenant_id, session_id),
    foreign key (tenant_id, session_id, parent_turn_id)
        references interview.turn(tenant_id, session_id, turn_id),
    foreign key (
        tenant_id, session_id, planned_position,
        question_version_id, question_version_no, question_content_hash,
        rubric_version_id, rubric_version_no, rubric_content_hash,
        topic_code, time_budget_millis, follow_up_budget)
        references interview.session_plan_question(
            tenant_id, session_id, position,
            question_version_id, question_version_no, question_content_hash,
            rubric_version_id, rubric_version_no, rubric_content_hash,
            topic_code, time_budget_millis, follow_up_budget),
    check ((kind = 'PRIMARY') = (parent_turn_id is null)),
    check ((prompt_key_id is null) = (prompt_algorithm is null)
        and (prompt_key_id is null) = (prompt_nonce is null)
        and (prompt_key_id is null) = (prompt_ciphertext is null)
        and (prompt_key_id is null) = (prompt_aad_hash is null)
        and (prompt_key_id is null) = (prompt_content_hash is null)
        and (prompt_key_id is null) = (committed_at is null)),
    check ((prompt_ref_id is null) = (prompt_ref_version_no is null)
        and (prompt_ref_id is null) = (prompt_ref_content_hash is null)),
    check ((schema_ref_id is null) = (schema_ref_version_no is null)
        and (schema_ref_id is null) = (schema_ref_content_hash is null))
);

create table interview.answer_version (
    tenant_id varchar(128) not null,
    answer_version_id varchar(128) not null,
    session_id varchar(128) not null,
    turn_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    source varchar(32) not null check (source in ('TEXT','CONFIRMED_TRANSCRIPT')),
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null,
    body_algorithm varchar(64) not null,
    body_nonce bytea not null,
    body_ciphertext bytea not null,
    body_aad_hash varchar(128) not null,
    transcript_version_id varchar(128),
    confirmed_by_ruoyi_user_id bigint not null,
    confirmed_at timestamptz not null,
    supersedes_id varchar(128),
    primary key (tenant_id, answer_version_id),
    unique (tenant_id, session_id, turn_id, version_no),
    unique (tenant_id, session_id, turn_id, answer_version_id),
    foreign key (tenant_id, session_id, turn_id)
        references interview.turn(tenant_id, session_id, turn_id),
    foreign key (tenant_id, confirmed_by_ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    foreign key (tenant_id, session_id, turn_id, supersedes_id)
        references interview.answer_version(tenant_id, session_id, turn_id, answer_version_id),
    check ((source = 'CONFIRMED_TRANSCRIPT') = (transcript_version_id is not null)),
    check (supersedes_id is null or supersedes_id <> answer_version_id)
);

create index session_recovery_idx on interview.session (
    tenant_id, ruoyi_user_id, state, recovery_expires_at, session_id);

create trigger session_plan_question_immutable
before update or delete on interview.session_plan_question
for each row execute function platform.reject_immutable_row_mutation();

create trigger interview_answer_version_immutable
before update or delete on interview.answer_version
for each row execute function platform.reject_immutable_row_mutation();
