-- Password credential and server-side opaque Session storage. Candidate only; NotRun.

create table identity.password_credential (
    identifier_hash varchar(128) primary key,
    user_id varchar(128) not null unique references identity.user_account(user_id),
    password_hash varchar(512) not null,
    credential_version integer not null check (credential_version > 0),
    created_at timestamptz not null,
    updated_at timestamptz,
    foreign key (identifier_hash) references identity.user_identifier(identifier_hash),
    check (updated_at is null or updated_at >= created_at)
);

comment on table identity.password_credential is
    'EMAIL_PASSWORD credential; identifier is keyed-HMAC and password_hash is a one-way PasswordEncoder output';

create table identity.web_session (
    tenant_id varchar(128) not null,
    session_id varchar(128) not null,
    user_id varchar(128) not null,
    token_hash varchar(128) not null unique,
    csrf_hash varchar(128) not null,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    idle_expires_at timestamptz not null,
    last_seen_at timestamptz not null,
    revoked_at timestamptz,
    primary key (tenant_id, session_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    check (expires_at > issued_at),
    check (idle_expires_at > issued_at and idle_expires_at <= expires_at),
    check (last_seen_at >= issued_at),
    check (revoked_at is null or revoked_at >= issued_at)
);

create index web_session_active_token_idx
    on identity.web_session (token_hash, expires_at, idle_expires_at)
    where revoked_at is null;

create index web_session_principal_idx
    on identity.web_session (tenant_id, user_id, expires_at)
    where revoked_at is null;
