-- Explicit global/pre-tenant registration idempotency. Candidate only; NotRun.

create table platform.pre_tenant_idempotency_record (
    principal_scope_hash varchar(128) not null,
    operation varchar(160) not null,
    idempotency_key varchar(128) not null,
    request_hash varchar(128) not null,
    expires_at timestamptz not null,
    state varchar(32) not null check (state in ('PROCESSING','SUCCEEDED','FAILED_REPLAYABLE','EXPIRED')),
    resource_references jsonb not null check (jsonb_typeof(resource_references) = 'object'),
    response_status integer,
    error_code varchar(128),
    primary key (principal_scope_hash, operation, idempotency_key),
    check (response_status is null or response_status between 100 and 599),
    check (state <> 'PROCESSING' or (response_status is null and error_code is null)),
    check (state <> 'SUCCEEDED' or (response_status between 200 and 399 and error_code is null)),
    check (state <> 'FAILED_REPLAYABLE' or (response_status between 400 and 599 and error_code is not null))
);

comment on table platform.pre_tenant_idempotency_record is
    'Anonymous registration claim keyed only by non-reversible principal scope hash; it is not a fake tenant';

create index pre_tenant_idempotency_expiry_idx
    on platform.pre_tenant_idempotency_record (expires_at, principal_scope_hash, operation)
    where state in ('PROCESSING','FAILED_REPLAYABLE','EXPIRED');

create function platform.guard_pre_tenant_idempotency_request()
returns trigger
language plpgsql
as $$
begin
    if new.principal_scope_hash is distinct from old.principal_scope_hash
        or new.operation is distinct from old.operation
        or new.idempotency_key is distinct from old.idempotency_key
        or new.request_hash is distinct from old.request_hash
        or new.expires_at is distinct from old.expires_at then
        raise exception 'pre-tenant idempotency scope and request are immutable' using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger pre_tenant_idempotency_request_immutable
before update on platform.pre_tenant_idempotency_record
for each row execute function platform.guard_pre_tenant_idempotency_request();
