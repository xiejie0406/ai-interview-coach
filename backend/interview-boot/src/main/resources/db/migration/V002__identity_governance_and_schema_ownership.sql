-- Persistence candidate only. Flyway execution remains NotRun.
-- The Flyway migrator role becomes the physical owner. Runtime grants are deliberately
-- external to this repository so that an application login can never acquire DDL rights.
alter schema identity owner to current_user;
alter schema catalog owner to current_user;
alter schema practice owner to current_user;
alter schema interview owner to current_user;
alter schema voice owner to current_user;
alter schema agent owner to current_user;
alter schema evaluation owner to current_user;
alter schema learning owner to current_user;
alter schema billing owner to current_user;
alter schema governance owner to current_user;
alter schema operations owner to current_user;
alter schema platform owner to current_user;

comment on schema identity is 'Logical owner: identity module; physical owner: Flyway migrator current_user';
comment on schema governance is 'Logical owner: governance module; physical owner: Flyway migrator current_user';
comment on schema agent is 'Logical owner: agent orchestration module; physical owner: Flyway migrator current_user';
comment on schema evaluation is 'Logical owner: evaluation module; physical owner: Flyway migrator current_user';
comment on schema learning is 'Logical owner: learning module; physical owner: Flyway migrator current_user';
comment on schema operations is 'Logical owner: operations module; physical owner: Flyway migrator current_user';

create or replace function platform.reject_immutable_row_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'immutable row in %.% cannot be updated or deleted', tg_table_schema, tg_table_name
        using errcode = '55000';
end;
$$;

create table identity.user_account (
    user_id varchar(128) primary key,
    locale varchar(35) not null,
    time_zone varchar(64) not null,
    display_key_id varchar(255) not null,
    display_algorithm varchar(64) not null,
    display_nonce bytea not null,
    display_ciphertext bytea not null,
    display_aad_hash varchar(128) not null,
    status varchar(32) not null check (status in ('PENDING','ACTIVE','LOCKED','DISABLED','CLOSING','CLOSED')),
    aggregate_version bigint not null check (aggregate_version >= 0)
);

comment on table identity.user_account is
    'Explicit global allowlist: authentication principal metadata has no tenant in the current domain contract; display name is encrypted.';

create table identity.user_identifier (
    identifier_hash varchar(128) primary key,
    user_id varchar(128) not null references identity.user_account(user_id),
    channel varchar(32) not null,
    verified_at timestamptz not null,
    unique (channel, identifier_hash)
);

comment on table identity.user_identifier is
    'Explicit global allowlist. No writer exists in the current IdentityRepository contract; population must remain fail-closed until an approved identity-channel binding port exists.';

create table identity.tenant (
    tenant_id varchar(128) primary key,
    tenant_type varchar(16) not null check (tenant_type in ('PERSONAL','PLATFORM')),
    created_by_user_id varchar(128) not null references identity.user_account(user_id),
    owner_user_id varchar(128) references identity.user_account(user_id),
    display_key_id varchar(255) not null,
    display_algorithm varchar(64) not null,
    display_nonce bytea not null,
    display_ciphertext bytea not null,
    display_aad_hash varchar(128) not null,
    status varchar(32) not null check (status in ('ACTIVE','SUSPENDED','CLOSING','CLOSED')),
    aggregate_version bigint not null check (aggregate_version >= 0),
    check ((tenant_type = 'PERSONAL' and owner_user_id is not null)
        or (tenant_type = 'PLATFORM' and owner_user_id is null))
);

create table identity.membership (
    tenant_id varchar(128) not null,
    membership_id varchar(128) not null,
    user_id varchar(128) not null,
    role varchar(32) not null check (role in ('OWNER','MEMBER','CONTENT_ADMIN','OPS_ADMIN','SUPPORT','PRIVACY_AUDITOR','SUPER_ADMIN')),
    status varchar(16) not null check (status in ('ACTIVE','SUSPENDED','LEFT')),
    joined_at timestamptz not null,
    left_at timestamptz,
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, membership_id),
    unique (tenant_id, user_id),
    foreign key (tenant_id) references identity.tenant(tenant_id),
    foreign key (user_id) references identity.user_account(user_id),
    check ((status = 'LEFT') = (left_at is not null))
);

create index membership_active_user_idx
    on identity.membership (user_id, tenant_id)
    where status = 'ACTIVE';

create table identity.profile_version (
    tenant_id varchar(128) not null,
    profile_version_id varchar(128) not null,
    user_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null,
    body_algorithm varchar(64) not null,
    body_nonce bytea not null,
    body_ciphertext bytea not null,
    body_aad_hash varchar(128) not null,
    locale varchar(35) not null,
    time_zone varchar(64) not null,
    effective_at timestamptz not null,
    primary key (tenant_id, profile_version_id),
    unique (tenant_id, user_id, version_no),
    unique (tenant_id, profile_version_id, version_no, content_hash),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id)
);

comment on table identity.profile_version is
    'Immutable profile snapshot. Current application ports do not yet expose its writer/reader; rows must not be synthesized by another repository.';

create trigger profile_version_immutable
before update or delete on identity.profile_version
for each row execute function platform.reject_immutable_row_mutation();

create table governance.consent_policy_version (
    tenant_id varchar(128) not null,
    policy_version_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    content_hash varchar(128) not null,
    purpose varchar(32) not null check (purpose in ('SERVICE_TERMS','PRIVACY_NOTICE','VOICE_CAPTURE','MODEL_PROCESSING')),
    effective_from timestamptz not null,
    primary key (tenant_id, policy_version_id),
    unique (tenant_id, policy_version_id, version_no, content_hash),
    foreign key (tenant_id) references identity.tenant(tenant_id)
);

comment on table governance.consent_policy_version is
    'Immutable policy reference registry. No writer exists in the current governance port and consent append therefore fails closed until an approved policy version is registered.';

create trigger consent_policy_version_immutable
before update or delete on governance.consent_policy_version
for each row execute function platform.reject_immutable_row_mutation();

create table governance.consent_record (
    tenant_id varchar(128) not null,
    consent_record_id varchar(128) not null,
    user_id varchar(128) not null,
    policy_version_id varchar(128) not null,
    policy_version_no integer not null,
    policy_content_hash varchar(128) not null,
    purpose varchar(32) not null check (purpose in ('SERVICE_TERMS','PRIVACY_NOTICE','VOICE_CAPTURE','MODEL_PROCESSING')),
    action varchar(16) not null check (action in ('GRANTED','REVOKED')),
    source varchar(64) not null,
    effective_at timestamptz not null,
    supersedes_id varchar(128),
    primary key (tenant_id, consent_record_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, policy_version_id, policy_version_no, policy_content_hash)
        references governance.consent_policy_version(tenant_id, policy_version_id, version_no, content_hash),
    foreign key (tenant_id, supersedes_id) references governance.consent_record(tenant_id, consent_record_id),
    check (supersedes_id is null or supersedes_id <> consent_record_id),
    check (action <> 'REVOKED' or supersedes_id is not null)
);

create index consent_history_idx
    on governance.consent_record (tenant_id, user_id, purpose, effective_at, consent_record_id);

create trigger consent_record_immutable
before update or delete on governance.consent_record
for each row execute function platform.reject_immutable_row_mutation();
