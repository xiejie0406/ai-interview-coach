-- Owner-scoped SSE recovery facts；不保存 Token、音频、完整回答或完整转写。
create table platform.stream_head (
    tenant_id varchar(128) not null,
    stream_type varchar(32) not null check (stream_type in ('INTERVIEW','EVALUATION')),
    stream_id varchar(128) not null,
    latest_sequence bigint not null check (latest_sequence >= 0),
    latest_event_id varchar(128),
    retained_from_sequence bigint not null check (retained_from_sequence > 0),
    updated_at timestamptz not null,
    primary key (tenant_id, stream_type, stream_id),
    foreign key (tenant_id) references platform.business_tenant(tenant_id),
    check ((latest_sequence = 0) = (latest_event_id is null)),
    check (retained_from_sequence <= latest_sequence + 1)
);

create table platform.stream_event (
    tenant_id varchar(128) not null,
    stream_type varchar(32) not null check (stream_type in ('INTERVIEW','EVALUATION')),
    stream_id varchar(128) not null,
    sequence bigint not null check (sequence > 0),
    event_id varchar(128) not null,
    aggregate_id varchar(128) not null,
    aggregate_version bigint not null check (aggregate_version >= 0),
    event_type varchar(160) not null,
    occurred_at timestamptz not null,
    schema_version integer not null check (schema_version > 0),
    correlation_id varchar(128) not null,
    event_data jsonb not null check (jsonb_typeof(event_data) = 'object'),
    expires_at timestamptz not null,
    primary key (tenant_id, stream_type, stream_id, sequence),
    unique (tenant_id, event_id),
    unique (tenant_id, stream_type, stream_id, event_id),
    foreign key (tenant_id, stream_type, stream_id)
        references platform.stream_head(tenant_id, stream_type, stream_id),
    check (expires_at > occurred_at)
);

create index stream_event_replay_idx on platform.stream_event (
    tenant_id, stream_type, stream_id, sequence, event_id);
create index stream_event_retention_idx on platform.stream_event (
    expires_at, tenant_id, stream_type, stream_id, sequence);

create function platform.guard_stream_head_monotonic()
returns trigger
language plpgsql
as $$
begin
    if new.tenant_id is distinct from old.tenant_id
        or new.stream_type is distinct from old.stream_type
        or new.stream_id is distinct from old.stream_id then
        raise exception 'stream head identity is immutable' using errcode = '55000';
    end if;
    if new.latest_sequence < old.latest_sequence
        or new.latest_sequence > old.latest_sequence + 1 then
        raise exception 'stream sequence must advance exactly once or remain unchanged'
            using errcode = '55000';
    end if;
    if new.latest_sequence = old.latest_sequence
        and new.latest_event_id is distinct from old.latest_event_id then
        raise exception 'stream event id cannot change without sequence advance'
            using errcode = '55000';
    end if;
    if new.latest_sequence = old.latest_sequence + 1
        and (new.latest_event_id is null
            or new.latest_event_id is not distinct from old.latest_event_id) then
        raise exception 'stream sequence advance requires a new event id'
            using errcode = '55000';
    end if;
    if new.retained_from_sequence < old.retained_from_sequence
        or new.retained_from_sequence > new.latest_sequence + 1 then
        raise exception 'stream retention floor must advance monotonically within the high-water mark'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger stream_head_monotonic
before update on platform.stream_head
for each row execute function platform.guard_stream_head_monotonic();

create trigger stream_event_immutable
before update on platform.stream_event
for each row execute function platform.reject_immutable_row_mutation();
