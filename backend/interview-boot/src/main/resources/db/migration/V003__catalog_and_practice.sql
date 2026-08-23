-- Catalog owns immutable question/rubric versions. Practice owns user attempts and answers.
-- Persistence candidate only; execution remains NotRun.
comment on schema catalog is 'Logical owner: catalog module; physical owner: Flyway migrator current_user';
comment on schema practice is 'Logical owner: practice module; physical owner: Flyway migrator current_user';

create table catalog.question (
    tenant_id varchar(128) not null,
    question_id varchar(128) not null,
    stable_key varchar(128) not null,
    status varchar(32) not null check (status in ('DRAFT','IN_REVIEW','PUBLISHED','PUBLISHED_WITH_DRAFT','PUBLISHED_WITH_REVIEW','RETIRED')),
    draft_version_id varchar(128),
    draft_version_no integer,
    draft_content_hash varchar(128),
    published_version_id varchar(128),
    published_version_no integer,
    published_content_hash varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, question_id),
    unique (tenant_id, stable_key),
    foreign key (tenant_id) references identity.tenant(tenant_id),
    check ((draft_version_id is null) = (draft_version_no is null)
        and (draft_version_id is null) = (draft_content_hash is null)),
    check ((published_version_id is null) = (published_version_no is null)
        and (published_version_id is null) = (published_content_hash is null))
);

create table catalog.question_version (
    tenant_id varchar(128) not null,
    question_version_id varchar(128) not null,
    question_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null,
    body_algorithm varchar(64) not null,
    body_nonce bytea not null,
    body_ciphertext bytea not null,
    body_aad_hash varchar(128) not null,
    difficulty varchar(64) not null,
    target_roles jsonb not null check (jsonb_typeof(target_roles) = 'array'),
    locale varchar(35) not null,
    source_id varchar(128),
    source_version_id varchar(128),
    source_version_no integer,
    source_content_hash varchar(128),
    source_license_code varchar(128),
    source_verification_fact_id varchar(128),
    source_verified_at timestamptz,
    authored_by varchar(128) not null,
    reviewed_by varchar(128),
    created_at timestamptz not null,
    primary key (tenant_id, question_version_id),
    unique (tenant_id, question_id, version_no),
    unique (tenant_id, question_version_id, version_no, content_hash),
    unique (tenant_id, question_id, question_version_id, version_no, content_hash),
    foreign key (tenant_id, question_id) references catalog.question(tenant_id, question_id)
        deferrable initially deferred,
    foreign key (tenant_id, authored_by) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, reviewed_by) references identity.membership(tenant_id, user_id),
    check ((source_id is null) = (source_version_id is null)
        and (source_id is null) = (source_version_no is null)
        and (source_id is null) = (source_content_hash is null)
        and (source_id is null) = (source_license_code is null)
        and (source_id is null) = (source_verification_fact_id is null)
        and (source_id is null) = (source_verified_at is null))
);

alter table catalog.question
    add constraint question_draft_version_fk
        foreign key (tenant_id, question_id, draft_version_id, draft_version_no, draft_content_hash)
        references catalog.question_version(
            tenant_id, question_id, question_version_id, version_no, content_hash)
        deferrable initially deferred,
    add constraint question_published_version_fk
        foreign key (tenant_id, question_id, published_version_id, published_version_no, published_content_hash)
        references catalog.question_version(
            tenant_id, question_id, question_version_id, version_no, content_hash)
        deferrable initially deferred;

create table catalog.rubric_version (
    tenant_id varchar(128) not null,
    rubric_version_id varchar(128) not null,
    question_version_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null,
    body_algorithm varchar(64) not null,
    body_nonce bytea not null,
    body_ciphertext bytea not null,
    body_aad_hash varchar(128) not null,
    created_at timestamptz not null,
    primary key (tenant_id, rubric_version_id),
    unique (tenant_id, question_version_id),
    unique (tenant_id, rubric_version_id, version_no, content_hash),
    unique (tenant_id, rubric_version_id, version_no, content_hash, question_version_id),
    foreign key (tenant_id, question_version_id) references catalog.question_version(tenant_id, question_version_id)
);

create table catalog.question_publication (
    tenant_id varchar(128) not null,
    publication_id varchar(128) not null,
    question_id varchar(128) not null,
    question_version_id varchar(128) not null,
    question_version_no integer not null,
    question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null,
    rubric_version_no integer not null,
    rubric_content_hash varchar(128) not null,
    source_verification_fact_id varchar(128) not null,
    reviewed_by varchar(128) not null,
    reason_code varchar(64) not null,
    published_at timestamptz not null,
    primary key (tenant_id, publication_id),
    unique (tenant_id, question_version_id),
    foreign key (tenant_id, question_id) references catalog.question(tenant_id, question_id),
    foreign key (tenant_id, question_id, question_version_id, question_version_no, question_content_hash)
        references catalog.question_version(
            tenant_id, question_id, question_version_id, version_no, content_hash),
    foreign key (
        tenant_id, rubric_version_id, rubric_version_no, rubric_content_hash, question_version_id)
        references catalog.rubric_version(
            tenant_id, rubric_version_id, version_no, content_hash, question_version_id),
    foreign key (tenant_id, reviewed_by) references identity.membership(tenant_id, user_id),
    check (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
);

create index published_question_filter_idx
    on catalog.question_version (tenant_id, locale, difficulty, created_at, question_version_id);

create trigger question_version_immutable
before update or delete on catalog.question_version
for each row execute function platform.reject_immutable_row_mutation();
create trigger rubric_version_immutable
before update or delete on catalog.rubric_version
for each row execute function platform.reject_immutable_row_mutation();
create trigger question_publication_immutable
before update or delete on catalog.question_publication
for each row execute function platform.reject_immutable_row_mutation();

create table practice.attempt (
    tenant_id varchar(128) not null,
    attempt_id varchar(128) not null,
    user_id varchar(128) not null,
    question_version_id varchar(128) not null,
    question_version_no integer not null,
    question_content_hash varchar(128) not null,
    rubric_version_id varchar(128) not null,
    rubric_version_no integer not null,
    rubric_content_hash varchar(128) not null,
    state varchar(16) not null check (state in ('DRAFT','SUBMITTED','CANCELLED')),
    started_at timestamptz not null,
    submitted_at timestamptz,
    cancelled_at timestamptz,
    draft_content_hash varchar(128),
    draft_saved_at timestamptz,
    draft_key_id varchar(255),
    draft_algorithm varchar(64),
    draft_nonce bytea,
    draft_ciphertext bytea,
    draft_aad_hash varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, attempt_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, question_version_id, question_version_no, question_content_hash)
        references catalog.question_version(tenant_id, question_version_id, version_no, content_hash),
    foreign key (
        tenant_id, rubric_version_id, rubric_version_no, rubric_content_hash, question_version_id)
        references catalog.rubric_version(
            tenant_id, rubric_version_id, version_no, content_hash, question_version_id),
    check ((draft_key_id is null) = (draft_algorithm is null)
        and (draft_key_id is null) = (draft_nonce is null)
        and (draft_key_id is null) = (draft_ciphertext is null)
        and (draft_key_id is null) = (draft_aad_hash is null)
        and (draft_key_id is null) = (draft_content_hash is null)
        and (draft_key_id is null) = (draft_saved_at is null)),
    check ((state = 'SUBMITTED') = (submitted_at is not null)),
    check ((state = 'CANCELLED') = (cancelled_at is not null))
);

create table practice.answer_version (
    tenant_id varchar(128) not null,
    answer_version_id varchar(128) not null,
    attempt_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    source varchar(32) not null check (source in ('TEXT','CONFIRMED_TRANSCRIPT')),
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null,
    body_algorithm varchar(64) not null,
    body_nonce bytea not null,
    body_ciphertext bytea not null,
    body_aad_hash varchar(128) not null,
    submitted_by varchar(128) not null,
    submitted_at timestamptz not null,
    supersedes_id varchar(128),
    primary key (tenant_id, answer_version_id),
    unique (tenant_id, attempt_id, version_no),
    unique (tenant_id, attempt_id, answer_version_id),
    foreign key (tenant_id, attempt_id) references practice.attempt(tenant_id, attempt_id),
    foreign key (tenant_id, submitted_by) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, attempt_id, supersedes_id)
        references practice.answer_version(tenant_id, attempt_id, answer_version_id),
    check (supersedes_id is null or supersedes_id <> answer_version_id)
);

create index practice_history_idx
    on practice.attempt (tenant_id, user_id, started_at desc, attempt_id desc);

create trigger practice_answer_version_immutable
before update or delete on practice.answer_version
for each row execute function platform.reject_immutable_row_mutation();
