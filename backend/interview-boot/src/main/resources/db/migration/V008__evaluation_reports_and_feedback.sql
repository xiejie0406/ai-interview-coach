-- Evaluation/Report persistence aligned to the structured-output domain contract.
-- User answer/transcript text is never copied here; report prose and feedback comments are encrypted.
-- Persistence candidate only; execution remains NotRun.
comment on schema evaluation is 'Logical owner: evaluation module; physical owner: Flyway migrator current_user';

create table evaluation.evidence_bundle (
    tenant_id varchar(128) not null,
    evidence_bundle_id varchar(128) not null,
    answer_version_id varchar(128) not null,
    answer_hash varchar(64) not null check (answer_hash ~ '^[a-f0-9]{64}$'),
    prompt_key varchar(160) not null,
    prompt_version integer not null check (prompt_version > 0),
    schema_key varchar(160) not null,
    schema_version integer not null check (schema_version > 0),
    primary key (tenant_id, evidence_bundle_id),
    unique (
        tenant_id, evidence_bundle_id, answer_version_id, answer_hash,
        prompt_key, prompt_version, schema_key, schema_version
    ),
    foreign key (tenant_id) references identity.tenant(tenant_id)
);

create table evaluation.evidence_item (
    tenant_id varchar(128) not null,
    evidence_bundle_id varchar(128) not null,
    position integer not null check (position > 0),
    evidence_id varchar(128) not null,
    evidence_type varchar(32) not null check (
        evidence_type in ('SUPPORTS','CONTRADICTS','OMISSION_SIGNAL','UNCLEAR')),
    start_inclusive integer not null check (start_inclusive >= 0),
    end_exclusive integer not null,
    quote_hash varchar(64) not null check (quote_hash ~ '^[a-f0-9]{64}$'),
    reason_code varchar(96),
    primary key (tenant_id, evidence_bundle_id, position),
    unique (tenant_id, evidence_bundle_id, evidence_id),
    foreign key (tenant_id, evidence_bundle_id)
        references evaluation.evidence_bundle(tenant_id, evidence_bundle_id),
    check (end_exclusive > start_inclusive)
);

create table evaluation.rubric_judgement (
    tenant_id varchar(128) not null,
    judgement_id varchar(128) not null,
    prompt_key varchar(160) not null,
    prompt_version integer not null check (prompt_version > 0),
    schema_key varchar(160) not null,
    schema_version integer not null check (schema_version > 0),
    rubric_version_id varchar(128) not null,
    limitations_key_id varchar(255) not null,
    limitations_algorithm varchar(64) not null,
    limitations_nonce bytea not null,
    limitations_ciphertext bytea not null,
    limitations_aad_hash varchar(128) not null,
    primary key (tenant_id, judgement_id),
    unique (
        tenant_id, judgement_id, prompt_key, prompt_version,
        schema_key, schema_version, rubric_version_id
    ),
    foreign key (tenant_id) references identity.tenant(tenant_id),
    foreign key (tenant_id, rubric_version_id)
        references catalog.rubric_version(tenant_id, rubric_version_id)
);

create table evaluation.rubric_dimension (
    tenant_id varchar(128) not null,
    judgement_id varchar(128) not null,
    position integer not null check (position > 0),
    dimension_id varchar(128) not null,
    judgement varchar(32) not null check (
        judgement in ('CORRECT','PARTIAL','INCORRECT','INSUFFICIENT','CONFLICTING')),
    confidence varchar(16) not null check (confidence in ('LOW','MEDIUM','HIGH')),
    insufficient_evidence boolean not null,
    reason_codes jsonb not null check (jsonb_typeof(reason_codes) = 'array'),
    evidence_ids jsonb not null check (jsonb_typeof(evidence_ids) = 'array'),
    primary key (tenant_id, judgement_id, position),
    unique (tenant_id, judgement_id, dimension_id),
    foreign key (tenant_id, judgement_id)
        references evaluation.rubric_judgement(tenant_id, judgement_id),
    check (insufficient_evidence = (judgement = 'INSUFFICIENT'))
);

create table evaluation.evaluation_run (
    tenant_id varchar(128) not null,
    evaluation_id varchar(128) not null,
    user_id varchar(128) not null,
    answer_version_id varchar(128) not null,
    answer_hash varchar(64) not null check (answer_hash ~ '^[a-f0-9]{64}$'),
    -- 最新 EvaluationSourceRef 仍是 interview-only；Practice source variant 未批准前禁止伪造这些引用。
    source_interview_id varchar(128) not null,
    source_plan_id varchar(128) not null,
    source_plan_version_no integer not null check (source_plan_version_no > 0),
    confirmed_transcript_version_id varchar(128),
    config_version_id varchar(128) not null,
    evidence_prompt_key varchar(160) not null,
    evidence_prompt_version integer not null check (evidence_prompt_version > 0),
    evidence_schema_key varchar(160) not null,
    evidence_schema_version integer not null check (evidence_schema_version > 0),
    judge_prompt_key varchar(160) not null,
    judge_prompt_version integer not null check (judge_prompt_version > 0),
    judge_schema_key varchar(160) not null,
    judge_schema_version integer not null check (judge_schema_version > 0),
    report_prompt_key varchar(160) not null,
    report_prompt_version integer not null check (report_prompt_version > 0),
    report_schema_key varchar(160) not null,
    report_schema_version integer not null check (report_schema_version > 0),
    provider_route_plan_id varchar(128) not null,
    primary_provider varchar(128) not null,
    primary_model varchar(160) not null,
    fallback_provider varchar(128),
    fallback_model varchar(160),
    safety_flags jsonb not null check (jsonb_typeof(safety_flags) = 'object'),
    rubric_version_id varchar(128) not null,
    status varchar(32) not null check (status in (
        'PENDING','RUNNING','SUCCEEDED','FAILED_RETRYABLE','FAILED_FINAL','CANCELLED')),
    stage varchar(32) not null check (stage in (
        'QUEUED','EVIDENCE_EXTRACTING','RUBRIC_JUDGING','REPORT_COMPOSING','MANUAL_REVIEW','COMPLETE')),
    evidence_bundle_id varchar(128),
    judgement_id varchar(128),
    evaluation_version_id varchar(128),
    report_id varchar(128),
    failure_code varchar(128),
    requested_at timestamptz not null,
    completed_at timestamptz,
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, evaluation_id),
    unique (tenant_id, evaluation_id, user_id),
    unique (tenant_id, evaluation_id, user_id, source_interview_id),
    unique (tenant_id, evaluation_version_id),
    unique (tenant_id, evaluation_id, evaluation_version_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, source_interview_id, user_id)
        references interview.session(tenant_id, session_id, user_id),
    foreign key (tenant_id, source_plan_id, source_plan_version_no, user_id)
        references interview.plan(tenant_id, plan_id, plan_version_no, user_id),
    foreign key (tenant_id, confirmed_transcript_version_id)
        references voice.transcript_version(tenant_id, transcript_version_id),
    foreign key (tenant_id, rubric_version_id)
        references catalog.rubric_version(tenant_id, rubric_version_id),
    foreign key (
        tenant_id, evidence_bundle_id, answer_version_id, answer_hash,
        evidence_prompt_key, evidence_prompt_version,
        evidence_schema_key, evidence_schema_version
    ) references evaluation.evidence_bundle (
        tenant_id, evidence_bundle_id, answer_version_id, answer_hash,
        prompt_key, prompt_version, schema_key, schema_version
    ),
    foreign key (
        tenant_id, judgement_id, judge_prompt_key, judge_prompt_version,
        judge_schema_key, judge_schema_version, rubric_version_id
    ) references evaluation.rubric_judgement (
        tenant_id, judgement_id, prompt_key, prompt_version,
        schema_key, schema_version, rubric_version_id
    ),
    check ((fallback_provider is null) = (fallback_model is null)),
    check ((status in ('SUCCEEDED','FAILED_FINAL','CANCELLED')) = (completed_at is not null)),
    check (status <> 'SUCCEEDED' or (stage = 'COMPLETE' and report_id is not null)),
    check (stage not in ('RUBRIC_JUDGING','REPORT_COMPOSING','COMPLETE')
        or evidence_bundle_id is not null),
    check (stage not in ('REPORT_COMPOSING','COMPLETE')
        or (judgement_id is not null and evaluation_version_id is not null)),
    check (status not in ('FAILED_RETRYABLE','FAILED_FINAL') and stage <> 'MANUAL_REVIEW'
        or failure_code is not null)
);

create index evaluation_owner_idx
    on evaluation.evaluation_run (tenant_id, user_id, requested_at desc, evaluation_id desc);
create index evaluation_interview_idx
    on evaluation.evaluation_run (
        tenant_id, source_interview_id, user_id, requested_at desc, evaluation_id desc);

create function evaluation.guard_confirmed_transcript_scope()
returns trigger
language plpgsql
as $$
begin
    if new.confirmed_transcript_version_id is not null and not exists (
        select 1
          from voice.transcript_version tv
          join voice.transcript t
            on t.tenant_id = tv.tenant_id and t.transcript_id = tv.transcript_id
         where tv.tenant_id = new.tenant_id
           and tv.transcript_version_id = new.confirmed_transcript_version_id
           and t.session_id = new.source_interview_id
           and t.state = 'CONFIRMED'
           and t.confirmed_version_id = tv.transcript_version_id
           and t.confirmed_by = new.user_id
    ) then
        raise exception 'evaluation transcript version is not the owner-confirmed interview version'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create constraint trigger evaluation_confirmed_transcript_scope
after insert or update on evaluation.evaluation_run
deferrable initially deferred
for each row execute function evaluation.guard_confirmed_transcript_scope();

create table evaluation.report (
    tenant_id varchar(128) not null,
    report_id varchar(128) not null,
    user_id varchar(128) not null,
    evaluation_id varchar(128) not null,
    source_interview_id varchar(128) not null,
    status varchar(16) not null check (status in ('PENDING','RUNNING','READY','PARTIAL','FAILED','CANCELLED')),
    current_report_version_id varchar(128),
    failure_code varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, report_id),
    unique (tenant_id, report_id, user_id),
    unique (tenant_id, report_id, evaluation_id),
    unique (tenant_id, report_id, evaluation_id, user_id),
    unique (tenant_id, evaluation_id),
    unique (tenant_id, source_interview_id),
    foreign key (tenant_id, evaluation_id, user_id, source_interview_id)
        references evaluation.evaluation_run(
            tenant_id, evaluation_id, user_id, source_interview_id),
    foreign key (tenant_id, source_interview_id, user_id)
        references interview.session(tenant_id, session_id, user_id),
    check ((status in ('READY','PARTIAL')) = (current_report_version_id is not null)),
    check (status <> 'FAILED' or failure_code is not null)
);

create table evaluation.report_version (
    tenant_id varchar(128) not null,
    report_version_id varchar(128) not null,
    report_id varchar(128) not null,
    evaluation_id varchar(128) not null,
    evaluation_version_id varchar(128) not null,
    prompt_key varchar(160) not null,
    prompt_version integer not null check (prompt_version > 0),
    schema_key varchar(160) not null,
    schema_version integer not null check (schema_version > 0),
    composition_hash varchar(64) not null check (composition_hash ~ '^[a-f0-9]{64}$'),
    composition_key_id varchar(255) not null,
    composition_algorithm varchar(64) not null,
    composition_nonce bytea not null,
    composition_ciphertext bytea not null,
    composition_aad_hash varchar(128) not null,
    created_at timestamptz not null,
    primary key (tenant_id, report_version_id),
    unique (tenant_id, report_id),
    unique (tenant_id, report_id, report_version_id),
    foreign key (tenant_id, report_id) references evaluation.report(tenant_id, report_id)
        deferrable initially deferred,
    foreign key (tenant_id, report_id, evaluation_id)
        references evaluation.report(tenant_id, report_id, evaluation_id)
        deferrable initially deferred,
    foreign key (tenant_id, evaluation_id, evaluation_version_id)
        references evaluation.evaluation_run(
            tenant_id, evaluation_id, evaluation_version_id)
);

alter table evaluation.report
    add constraint report_current_version_fk
        foreign key (tenant_id, report_id, current_report_version_id)
        references evaluation.report_version(tenant_id, report_id, report_version_id)
        deferrable initially deferred;

alter table evaluation.evaluation_run
    add constraint evaluation_report_fk
        foreign key (tenant_id, report_id, evaluation_id, user_id)
        references evaluation.report(tenant_id, report_id, evaluation_id, user_id)
        deferrable initially deferred;

create table evaluation.report_feedback (
    tenant_id varchar(128) not null,
    report_id varchar(128) not null,
    evaluation_id varchar(128) not null,
    feedback_id varchar(128) not null,
    author_user_id varchar(128) not null,
    feedback_type varchar(32) not null check (
        feedback_type in ('INACCURATE','UNHELPFUL','MISSING_CONTEXT','OTHER')),
    comment_ref varchar(256),
    created_at timestamptz not null,
    primary key (tenant_id, feedback_id),
    unique (tenant_id, report_id, feedback_id),
    unique (tenant_id, feedback_id, evaluation_id, author_user_id),
    foreign key (tenant_id, report_id, evaluation_id, author_user_id)
        references evaluation.report(tenant_id, report_id, evaluation_id, user_id),
    foreign key (tenant_id, author_user_id)
        references identity.membership(tenant_id, user_id)
);

create table evaluation.feedback_content (
    tenant_id varchar(128) not null,
    feedback_id varchar(128) not null,
    evaluation_id varchar(128) not null,
    user_id varchar(128) not null,
    content_ref varchar(256) not null,
    content_key_id varchar(255) not null,
    content_algorithm varchar(64) not null,
    content_nonce bytea not null,
    content_ciphertext bytea not null,
    content_aad_hash varchar(128) not null,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, feedback_id),
    unique (tenant_id, feedback_id, content_ref),
    foreign key (tenant_id, feedback_id, evaluation_id, user_id)
        references evaluation.report_feedback(
            tenant_id, feedback_id, evaluation_id, author_user_id)
        deferrable initially deferred,
    foreign key (tenant_id, evaluation_id, user_id)
        references evaluation.evaluation_run(tenant_id, evaluation_id, user_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id)
);

alter table evaluation.report_feedback
    add constraint report_feedback_content_fk
        foreign key (tenant_id, feedback_id, comment_ref)
        references evaluation.feedback_content(tenant_id, feedback_id, content_ref)
        deferrable initially deferred;

create index report_feedback_history_idx
    on evaluation.report_feedback (tenant_id, report_id, created_at, feedback_id);
create index feedback_content_owner_idx
    on evaluation.feedback_content (tenant_id, user_id, evaluation_id, feedback_id);

create function evaluation.guard_run_identity()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.evaluation_id is distinct from old.evaluation_id
        or new.user_id is distinct from old.user_id
        or new.answer_version_id is distinct from old.answer_version_id
        or new.answer_hash is distinct from old.answer_hash
        or new.source_interview_id is distinct from old.source_interview_id
        or new.source_plan_id is distinct from old.source_plan_id
        or new.source_plan_version_no is distinct from old.source_plan_version_no
        or new.confirmed_transcript_version_id is distinct from old.confirmed_transcript_version_id
        or new.config_version_id is distinct from old.config_version_id
        or new.evidence_prompt_key is distinct from old.evidence_prompt_key
        or new.evidence_prompt_version is distinct from old.evidence_prompt_version
        or new.evidence_schema_key is distinct from old.evidence_schema_key
        or new.evidence_schema_version is distinct from old.evidence_schema_version
        or new.judge_prompt_key is distinct from old.judge_prompt_key
        or new.judge_prompt_version is distinct from old.judge_prompt_version
        or new.judge_schema_key is distinct from old.judge_schema_key
        or new.judge_schema_version is distinct from old.judge_schema_version
        or new.report_prompt_key is distinct from old.report_prompt_key
        or new.report_prompt_version is distinct from old.report_prompt_version
        or new.report_schema_key is distinct from old.report_schema_key
        or new.report_schema_version is distinct from old.report_schema_version
        or new.provider_route_plan_id is distinct from old.provider_route_plan_id
        or new.primary_provider is distinct from old.primary_provider
        or new.primary_model is distinct from old.primary_model
        or new.fallback_provider is distinct from old.fallback_provider
        or new.fallback_model is distinct from old.fallback_model
        or new.safety_flags is distinct from old.safety_flags
        or new.rubric_version_id is distinct from old.rubric_version_id
        or new.requested_at is distinct from old.requested_at then
        raise exception 'evaluation source and generation policy are immutable'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger evaluation_run_identity_immutable
before update on evaluation.evaluation_run
for each row execute function evaluation.guard_run_identity();

create function evaluation.guard_report_identity()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.report_id is distinct from old.report_id
        or new.user_id is distinct from old.user_id
        or new.evaluation_id is distinct from old.evaluation_id
        or new.source_interview_id is distinct from old.source_interview_id then
        raise exception 'evaluation report ownership and source are immutable'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger evaluation_report_identity_immutable
before update on evaluation.report
for each row execute function evaluation.guard_report_identity();

create trigger evidence_bundle_immutable
before update or delete on evaluation.evidence_bundle
for each row execute function platform.reject_immutable_row_mutation();
create trigger evidence_item_immutable
before update or delete on evaluation.evidence_item
for each row execute function platform.reject_immutable_row_mutation();
create trigger rubric_judgement_immutable
before update or delete on evaluation.rubric_judgement
for each row execute function platform.reject_immutable_row_mutation();
create trigger rubric_dimension_immutable
before update or delete on evaluation.rubric_dimension
for each row execute function platform.reject_immutable_row_mutation();
create trigger report_version_immutable
before update or delete on evaluation.report_version
for each row execute function platform.reject_immutable_row_mutation();
create trigger report_feedback_immutable
before update or delete on evaluation.report_feedback
for each row execute function platform.reject_immutable_row_mutation();
create trigger feedback_content_immutable
before update or delete on evaluation.feedback_content
for each row execute function platform.reject_immutable_row_mutation();
