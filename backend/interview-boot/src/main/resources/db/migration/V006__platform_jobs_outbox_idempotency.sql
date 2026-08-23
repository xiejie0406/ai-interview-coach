-- Platform reliability primitives. Job/outbox scanners rely on FOR UPDATE SKIP LOCKED
-- inside an application TransactionPort boundary; idempotency claim is INSERT-on-conflict.
-- Persistence candidate only; execution remains NotRun.
comment on schema platform is 'Logical owner: platform reliability primitives; physical owner: Flyway migrator current_user';

create table platform.job (
    tenant_id varchar(128) not null,
    job_id varchar(128) not null,
    job_type varchar(128) not null,
    business_operation_id varchar(128) not null,
    payload_references jsonb not null check (jsonb_typeof(payload_references) = 'object'),
    max_attempts integer not null check (max_attempts > 0),
    state varchar(32) not null check (state in ('PENDING','RUNNING','FAILED_RETRYABLE','FAILED_FINAL','CANCEL_REQUESTED','CANCELLED','SUCCEEDED')),
    available_at timestamptz not null,
    lease_owner varchar(128),
    lease_expires_at timestamptz,
    heartbeat_at timestamptz,
    attempt_count integer not null check (attempt_count >= 0 and attempt_count <= max_attempts),
    last_error_code varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, job_id),
    unique (tenant_id, job_type, business_operation_id),
    foreign key (tenant_id) references identity.tenant(tenant_id),
    check ((state in ('RUNNING','CANCEL_REQUESTED')) = (lease_owner is not null)),
    check ((lease_owner is null) = (lease_expires_at is null)
        and (lease_owner is null) = (heartbeat_at is null)),
    check (state not in ('FAILED_RETRYABLE','FAILED_FINAL') or last_error_code is not null)
);

create index job_claim_idx
    on platform.job (job_type, available_at, tenant_id, job_id)
    where state = 'PENDING';

create function platform.guard_job_identity()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.job_id is distinct from old.job_id
        or new.job_type is distinct from old.job_type
        or new.business_operation_id is distinct from old.business_operation_id
        or new.payload_references is distinct from old.payload_references
        or new.max_attempts is distinct from old.max_attempts then
        raise exception 'job identity and payload references are immutable' using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger job_identity_immutable
before update on platform.job
for each row execute function platform.guard_job_identity();

create table platform.outbox_event (
    tenant_id varchar(128) not null,
    event_id varchar(128) not null,
    aggregate_type varchar(128) not null,
    aggregate_id varchar(128) not null,
    source_aggregate_version bigint not null check (source_aggregate_version >= 0),
    event_type varchar(160) not null,
    schema_version integer not null check (schema_version > 0),
    correlation_id varchar(128) not null,
    payload_references jsonb not null check (jsonb_typeof(payload_references) = 'object'),
    occurred_at timestamptz not null,
    primary key (tenant_id, event_id),
    unique (tenant_id, aggregate_type, aggregate_id, source_aggregate_version, event_type),
    foreign key (tenant_id) references identity.tenant(tenant_id)
);

create table platform.outbox_delivery (
    tenant_id varchar(128) not null,
    event_id varchar(128) not null,
    state varchar(32) not null check (state in ('PENDING','CLAIMED','PUBLISHED','FAILED_RETRYABLE','FAILED_FINAL')),
    available_at timestamptz not null,
    claimed_by varchar(128),
    claim_expires_at timestamptz,
    published_at timestamptz,
    attempt_count integer not null check (attempt_count >= 0),
    last_error_code varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, event_id),
    foreign key (tenant_id, event_id) references platform.outbox_event(tenant_id, event_id),
    check ((state = 'CLAIMED') = (claimed_by is not null)),
    check ((claimed_by is null) = (claim_expires_at is null)),
    check ((state = 'PUBLISHED') = (published_at is not null)),
    check (state not in ('FAILED_RETRYABLE','FAILED_FINAL') or last_error_code is not null)
);

create index outbox_publish_idx
    on platform.outbox_delivery (available_at, tenant_id, event_id)
    where state in ('PENDING','FAILED_RETRYABLE');

create trigger outbox_event_immutable
before update or delete on platform.outbox_event
for each row execute function platform.reject_immutable_row_mutation();

create table platform.idempotency_record (
    tenant_id varchar(128) not null,
    idempotency_record_id varchar(128) not null,
    principal_ref_hash varchar(128) not null,
    operation varchar(160) not null,
    idempotency_key varchar(256) not null,
    request_hash varchar(128) not null,
    expires_at timestamptz not null,
    state varchar(32) not null check (state in ('PROCESSING','SUCCEEDED','FAILED_REPLAYABLE','EXPIRED')),
    resource_references jsonb not null check (jsonb_typeof(resource_references) = 'object'),
    response_status integer,
    error_code varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, idempotency_record_id),
    unique (tenant_id, principal_ref_hash, operation, idempotency_key),
    foreign key (tenant_id) references identity.tenant(tenant_id),
    check (response_status is null or response_status between 100 and 599),
    check (state <> 'PROCESSING' or (response_status is null and error_code is null)),
    check (state <> 'SUCCEEDED' or (response_status between 200 and 399 and error_code is null)),
    check (state <> 'FAILED_REPLAYABLE' or (response_status between 400 and 599 and error_code is not null))
);

create index idempotency_expiry_idx
    on platform.idempotency_record (tenant_id, expires_at, idempotency_record_id)
    where state in ('PROCESSING','FAILED_REPLAYABLE','EXPIRED');

create function platform.guard_idempotency_request()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.idempotency_record_id is distinct from old.idempotency_record_id
        or new.principal_ref_hash is distinct from old.principal_ref_hash
        or new.operation is distinct from old.operation
        or new.idempotency_key is distinct from old.idempotency_key
        or new.request_hash is distinct from old.request_hash
        or new.expires_at is distinct from old.expires_at then
        raise exception 'idempotency scope and request hash are immutable' using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger idempotency_request_immutable
before update on platform.idempotency_record
for each row execute function platform.guard_idempotency_request();
