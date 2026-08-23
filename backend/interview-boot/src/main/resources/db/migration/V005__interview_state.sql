-- Interview plans are mutable drafts; sessions preserve their own confirmed-plan snapshot.
-- User answers and committed prompt text are encrypted envelopes. Execution remains NotRun.
comment on schema interview is 'Logical owner: interview module; physical owner: Flyway migrator current_user';

create table interview.plan (
    tenant_id varchar(128) not null,
    plan_id varchar(128) not null,
    user_id varchar(128) not null,
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
    unique (tenant_id, plan_id, plan_version_no, user_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, profile_version_id, profile_version_no, profile_content_hash)
        references identity.profile_version(tenant_id, profile_version_id, version_no, content_hash),
    foreign key (tenant_id, usage_reservation_id, user_id)
        references billing.usage_reservation(tenant_id, reservation_id, user_id),
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
    foreign key (tenant_id, plan_id) references interview.plan(tenant_id, plan_id) on delete cascade,
    foreign key (tenant_id, question_version_id, question_version_no, question_content_hash)
        references catalog.question_version(tenant_id, question_version_id, version_no, content_hash),
    foreign key (
        tenant_id, rubric_version_id, rubric_version_no, rubric_content_hash, question_version_id)
        references catalog.rubric_version(
            tenant_id, rubric_version_id, version_no, content_hash, question_version_id)
);

create table interview.session (
    tenant_id varchar(128) not null,
    session_id varchar(128) not null,
    user_id varchar(128) not null,
    plan_id varchar(128) not null,
    plan_version_no integer not null,
    plan_content_hash varchar(128) not null,
    usage_reservation_id varchar(128) not null,
    plan_follow_up_budget integer not null check (plan_follow_up_budget >= 0),
    mode varchar(16) not null check (mode in ('TEXT','VOICE')),
    state varchar(32) not null check (state in ('READY','IN_PROGRESS','PAUSED','FAILED_RECOVERABLE','COMPLETING','COMPLETED','CANCELLED','FAILED_FINAL')),
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
    unique (tenant_id, session_id, user_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, plan_id, plan_version_no, plan_content_hash)
        references interview.plan(tenant_id, plan_id, plan_version_no, content_hash),
    foreign key (tenant_id, usage_reservation_id, user_id)
        references billing.usage_reservation(tenant_id, reservation_id, user_id),
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
    foreign key (tenant_id, session_id) references interview.session(tenant_id, session_id),
    foreign key (tenant_id, question_version_id, question_version_no, question_content_hash)
        references catalog.question_version(tenant_id, question_version_id, version_no, content_hash),
    foreign key (
        tenant_id, rubric_version_id, rubric_version_no, rubric_content_hash, question_version_id)
        references catalog.rubric_version(
            tenant_id, rubric_version_id, version_no, content_hash, question_version_id)
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
    state varchar(32) not null check (state in ('PLANNED','QUESTION_COMMITTED','ANSWER_CONFIRMED','CLOSED','SKIPPED','CANCELLED','FAILED')),
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
    foreign key (tenant_id, session_id) references interview.session(tenant_id, session_id),
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
    confirmed_by varchar(128) not null,
    confirmed_at timestamptz not null,
    supersedes_id varchar(128),
    primary key (tenant_id, answer_version_id),
    unique (tenant_id, session_id, turn_id, version_no),
    unique (tenant_id, session_id, turn_id, answer_version_id),
    foreign key (tenant_id, session_id, turn_id)
        references interview.turn(tenant_id, session_id, turn_id),
    foreign key (tenant_id, confirmed_by) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, session_id, turn_id, supersedes_id)
        references interview.answer_version(tenant_id, session_id, turn_id, answer_version_id),
    check ((source = 'CONFIRMED_TRANSCRIPT') = (transcript_version_id is not null)),
    check (supersedes_id is null or supersedes_id <> answer_version_id)
);

create index session_recovery_idx
    on interview.session (tenant_id, user_id, state, recovery_expires_at, session_id);

create trigger session_plan_question_immutable
before update or delete on interview.session_plan_question
for each row execute function platform.reject_immutable_row_mutation();
create trigger interview_answer_version_immutable
before update or delete on interview.answer_version
for each row execute function platform.reject_immutable_row_mutation();
