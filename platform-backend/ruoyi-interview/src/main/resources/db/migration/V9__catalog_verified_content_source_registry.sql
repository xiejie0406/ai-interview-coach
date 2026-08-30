-- 题库来源治理：只有 registry 中明确 VERIFIED 的来源版本才能作为发布依据。
-- 题目版本仍保存来源快照，registry 负责服务端解析和许可/证据事实；不接收客户端 verified 结论。

create table if not exists catalog.content_source (
    tenant_id varchar(128) not null,
    content_source_id varchar(128) not null,
    stable_key varchar(160) not null,
    status varchar(16) not null default 'DRAFT',
    current_version_id varchar(128),
    aggregate_version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    primary key (tenant_id, content_source_id),
    unique (tenant_id, stable_key),
    constraint catalog_content_source_status_ck
        check (status in ('DRAFT', 'VERIFIED', 'RETIRED', 'BLOCKED')),
    constraint catalog_content_source_version_pointer_ck
        check (current_version_id is not null or status in ('DRAFT', 'BLOCKED')),
    constraint catalog_content_source_aggregate_version_ck
        check (aggregate_version >= 0)
);

create table if not exists catalog.content_source_version (
    tenant_id varchar(128) not null,
    content_source_version_id varchar(128) not null,
    content_source_id varchar(128) not null,
    version_no integer not null,
    status varchar(16) not null default 'UNVERIFIED',
    source_type varchar(64) not null,
    title varchar(512) not null,
    source_url varchar(2048),
    publisher varchar(256),
    license_code varchar(128) not null,
    accessed_at timestamptz not null,
    attribution_text varchar(4000),
    content_hash varchar(128) not null,
    evidence_hash varchar(128) not null,
    verification_fact_id varchar(128),
    verified_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, content_source_version_id),
    unique (tenant_id, content_source_id, version_no),
    constraint catalog_content_source_version_status_ck
        check (status in ('UNVERIFIED', 'VERIFIED', 'RETIRED', 'BLOCKED')),
    constraint catalog_content_source_version_no_ck check (version_no > 0),
    constraint catalog_content_source_version_hash_ck
        check (btrim(content_hash) <> '' and btrim(evidence_hash) <> ''),
    constraint catalog_content_source_version_verified_ck
        check ((status = 'VERIFIED') = (verification_fact_id is not null and verified_at is not null))
);

alter table catalog.content_source_version
    add constraint catalog_content_source_version_source_fk
    foreign key (tenant_id, content_source_id)
    references catalog.content_source (tenant_id, content_source_id);

alter table catalog.content_source
    add constraint catalog_content_source_current_version_fk
    foreign key (tenant_id, current_version_id)
    references catalog.content_source_version (tenant_id, content_source_version_id);

create index if not exists catalog_content_source_verified_lookup_idx
    on catalog.content_source_version (tenant_id, content_source_version_id, status, verified_at);

comment on table catalog.content_source is
    'Server-owned source root; VERIFIED requires a current immutable version and is the only publication input';
comment on table catalog.content_source_version is
    'Immutable source/license/evidence snapshot; content_hash and evidence_hash are independently auditable';
