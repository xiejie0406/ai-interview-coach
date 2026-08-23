-- Learning plans pin their source ReportVersion and generation policy. Candidate prose is encrypted.
-- Persistence candidate only; execution remains NotRun.
comment on schema learning is 'Logical owner: learning module; physical owner: Flyway migrator current_user';

create table learning.plan (
    tenant_id varchar(128) not null,
    learning_plan_id varchar(128) not null,
    user_id varchar(128) not null,
    source_report_id varchar(128) not null,
    source_report_version_id varchar(128) not null,
    config_version_id varchar(128) not null,
    coach_prompt_key varchar(160) not null,
    coach_prompt_version integer not null check (coach_prompt_version > 0),
    coach_schema_key varchar(160) not null,
    coach_schema_version integer not null check (coach_schema_version > 0),
    provider_route_plan_id varchar(128) not null,
    primary_provider varchar(128) not null,
    primary_model varchar(160) not null,
    fallback_provider varchar(128),
    fallback_model varchar(160),
    safety_flags jsonb not null check (jsonb_typeof(safety_flags) = 'object'),
    limitations_key_id varchar(255) not null,
    limitations_algorithm varchar(64) not null,
    limitations_nonce bytea not null,
    limitations_ciphertext bytea not null,
    limitations_aad_hash varchar(128) not null,
    status varchar(16) not null check (status in ('CANDIDATE','CONFIRMED','COMPLETED','CANCELLED')),
    confirmed_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, learning_plan_id),
    unique (tenant_id, learning_plan_id, user_id),
    unique (tenant_id, user_id, source_report_id, source_report_version_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, source_report_id, user_id)
        references evaluation.report(tenant_id, report_id, user_id),
    foreign key (tenant_id, source_report_id, source_report_version_id)
        references evaluation.report_version(tenant_id, report_id, report_version_id),
    check ((fallback_provider is null) = (fallback_model is null)),
    check (status <> 'CANDIDATE' or confirmed_at is null),
    check (status not in ('CONFIRMED','COMPLETED') or confirmed_at is not null),
    check ((status = 'COMPLETED') = (completed_at is not null))
);

create table learning.item (
    tenant_id varchar(128) not null,
    learning_plan_id varchar(128) not null,
    learning_item_id varchar(128) not null,
    position integer not null check (position > 0),
    question_version_id varchar(128) not null,
    content_key_id varchar(255) not null,
    content_algorithm varchar(64) not null,
    content_nonce bytea not null,
    content_ciphertext bytea not null,
    content_aad_hash varchar(128) not null,
    reason_codes jsonb not null check (jsonb_typeof(reason_codes) = 'array'),
    priority integer not null check (priority between 1 and 5),
    scheduled_at timestamptz,
    status varchar(16) not null check (
        status in ('PENDING','IN_PROGRESS','COMPLETED','SKIPPED','CANCELLED')),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, learning_plan_id, learning_item_id),
    unique (tenant_id, learning_plan_id, position),
    unique (tenant_id, learning_item_id),
    foreign key (tenant_id, learning_plan_id)
        references learning.plan(tenant_id, learning_plan_id),
    foreign key (tenant_id, question_version_id)
        references catalog.question_version(tenant_id, question_version_id)
);

create index learning_plan_owner_idx
    on learning.plan (tenant_id, user_id, created_at desc, learning_plan_id desc);
create index learning_item_schedule_idx
    on learning.item (tenant_id, status, scheduled_at, learning_item_id)
    where status in ('PENDING','IN_PROGRESS');

create function learning.guard_plan_identity()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.learning_plan_id is distinct from old.learning_plan_id
        or new.user_id is distinct from old.user_id
        or new.source_report_id is distinct from old.source_report_id
        or new.source_report_version_id is distinct from old.source_report_version_id
        or new.config_version_id is distinct from old.config_version_id
        or new.coach_prompt_key is distinct from old.coach_prompt_key
        or new.coach_prompt_version is distinct from old.coach_prompt_version
        or new.coach_schema_key is distinct from old.coach_schema_key
        or new.coach_schema_version is distinct from old.coach_schema_version
        or new.provider_route_plan_id is distinct from old.provider_route_plan_id
        or new.primary_provider is distinct from old.primary_provider
        or new.primary_model is distinct from old.primary_model
        or new.fallback_provider is distinct from old.fallback_provider
        or new.fallback_model is distinct from old.fallback_model
        or new.safety_flags is distinct from old.safety_flags
        or new.limitations_key_id is distinct from old.limitations_key_id
        or new.limitations_algorithm is distinct from old.limitations_algorithm
        or new.limitations_nonce is distinct from old.limitations_nonce
        or new.limitations_ciphertext is distinct from old.limitations_ciphertext
        or new.limitations_aad_hash is distinct from old.limitations_aad_hash
        or new.created_at is distinct from old.created_at then
        raise exception 'learning plan pinned source and generation policy are immutable'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger learning_plan_identity_immutable
before update on learning.plan
for each row execute function learning.guard_plan_identity();

create function learning.guard_item_identity()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.learning_plan_id is distinct from old.learning_plan_id
        or new.learning_item_id is distinct from old.learning_item_id
        or new.position is distinct from old.position
        or new.question_version_id is distinct from old.question_version_id
        or new.content_key_id is distinct from old.content_key_id
        or new.content_algorithm is distinct from old.content_algorithm
        or new.content_nonce is distinct from old.content_nonce
        or new.content_ciphertext is distinct from old.content_ciphertext
        or new.content_aad_hash is distinct from old.content_aad_hash
        or new.reason_codes is distinct from old.reason_codes
        or new.priority is distinct from old.priority then
        raise exception 'learning item content and source are immutable' using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger learning_item_identity_immutable
before update on learning.item
for each row execute function learning.guard_item_identity();
