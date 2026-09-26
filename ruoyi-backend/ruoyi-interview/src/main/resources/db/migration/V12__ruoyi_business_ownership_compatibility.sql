-- 基线为 V7 的历史库没有执行 V1-V6，但迁入代码已切换到 RuoYi owner 引用。
-- 本迁移补齐业务所有权表，并为旧表增加并行 ruoyi_user_id 列；历史 user_id 数据保留，
-- 新流量只写 RuoYi 列，避免把旧 identity 账号当作 RuoYi 身份。

create schema if not exists platform;

create table if not exists platform.business_tenant (
    tenant_id varchar(128) primary key,
    owner_ruoyi_user_id bigint not null,
    tenant_type varchar(24) not null default 'PERSONAL',
    status varchar(24) not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    constraint business_tenant_type_ck check (tenant_type in ('PERSONAL', 'WORKSPACE', 'PLATFORM')),
    constraint business_tenant_status_ck check (status in ('ACTIVE', 'DISABLED'))
);

create table if not exists platform.ruoyi_user_binding (
    ruoyi_user_id bigint primary key,
    business_tenant_id varchar(128) not null,
    status varchar(24) not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint ruoyi_user_binding_status_ck check (status in ('ACTIVE', 'DISABLED')),
    constraint ruoyi_user_binding_tenant_fk foreign key (business_tenant_id)
        references platform.business_tenant(tenant_id)
);

create table if not exists platform.business_membership (
    tenant_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    business_role varchar(48) not null,
    status varchar(24) not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, ruoyi_user_id),
    constraint business_membership_status_ck check (status in ('ACTIVE', 'DISABLED')),
    constraint business_membership_tenant_fk foreign key (tenant_id)
        references platform.business_tenant(tenant_id)
);

create table if not exists platform.profile_version (
    business_tenant_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    profile_version_id varchar(128) not null,
    version_no integer not null,
    content_hash varchar(128) not null,
    profile_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    primary key (business_tenant_id, profile_version_id, version_no),
    unique (business_tenant_id, ruoyi_user_id, profile_version_id, version_no),
    unique (business_tenant_id, profile_version_id, version_no, content_hash),
    constraint profile_version_tenant_user_fk foreign key (business_tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id)
);

-- 仅历史 V7 基线包含 user_id。全新库已由 V1-V4 直接创建 RuoYi 列与外键，
-- 必须跳过本兼容段，否则会访问不存在的旧列并添加重复约束。
do $ownership_compatibility$
begin
if exists (
    select 1 from information_schema.columns
     where table_schema = 'interview' and table_name = 'plan' and column_name = 'user_id'
) then
alter table interview.plan add column if not exists ruoyi_user_id bigint;
alter table interview.session add column if not exists ruoyi_user_id bigint;
alter table interview.answer_version add column if not exists confirmed_by_ruoyi_user_id bigint;
alter table governance.consent_record add column if not exists ruoyi_user_id bigint;
alter table voice.transcript add column if not exists confirmed_by_ruoyi_user_id bigint;
alter table voice.transcript_version add column if not exists corrected_by_ruoyi_user_id bigint;

alter table interview.plan alter column user_id drop not null;
alter table interview.session alter column user_id drop not null;
alter table interview.answer_version alter column confirmed_by drop not null;
alter table governance.consent_record alter column user_id drop not null;

alter table interview.plan drop constraint if exists plan_tenant_id_user_id_fkey;
alter table interview.plan drop constraint if exists plan_tenant_id_profile_version_id_profile_version_no_profi_fkey;
alter table interview.session drop constraint if exists session_tenant_id_user_id_fkey;
alter table interview.answer_version drop constraint if exists answer_version_tenant_id_confirmed_by_fkey;
alter table governance.consent_record drop constraint if exists consent_record_tenant_id_user_id_fkey;
alter table voice.transcript drop constraint if exists transcript_tenant_id_confirmed_by_fkey;
alter table voice.transcript_version drop constraint if exists transcript_version_tenant_id_corrected_by_fkey;
alter table billing.entitlement drop constraint if exists entitlement_tenant_id_user_id_fkey;
alter table billing.usage_reservation drop constraint if exists usage_reservation_tenant_id_user_id_fkey;
alter table billing.usage_settlement drop constraint if exists usage_settlement_tenant_id_user_id_fkey;
alter table platform.idempotency_record drop constraint if exists idempotency_record_tenant_id_fkey;
alter table platform.job drop constraint if exists job_tenant_id_fkey;
alter table platform.outbox_event drop constraint if exists outbox_event_tenant_id_fkey;
alter table platform.stream_head drop constraint if exists stream_head_tenant_id_fkey;

alter table interview.plan
    add constraint plan_ruoyi_membership_fk foreign key (tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    add constraint plan_profile_business_fk foreign key
        (tenant_id, profile_version_id, profile_version_no, profile_content_hash)
        references platform.profile_version
        (business_tenant_id, profile_version_id, version_no, content_hash)
        not valid;

alter table interview.session
    add constraint session_ruoyi_membership_fk foreign key (tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id);

alter table interview.answer_version
    add constraint answer_version_ruoyi_membership_fk foreign key (tenant_id, confirmed_by_ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id);

alter table governance.consent_record
    add constraint consent_record_ruoyi_membership_fk foreign key (tenant_id, ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id);

alter table voice.transcript
    add constraint transcript_ruoyi_membership_fk foreign key (tenant_id, confirmed_by_ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id);

alter table voice.transcript_version
    add constraint transcript_version_ruoyi_membership_fk foreign key (tenant_id, corrected_by_ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id);

alter table platform.idempotency_record
    add constraint idempotency_record_business_tenant_fk foreign key (tenant_id)
        references platform.business_tenant(tenant_id) not valid;
alter table platform.job
    add constraint job_business_tenant_fk foreign key (tenant_id)
        references platform.business_tenant(tenant_id) not valid;
alter table platform.outbox_event
    add constraint outbox_event_business_tenant_fk foreign key (tenant_id)
        references platform.business_tenant(tenant_id) not valid;
alter table platform.stream_head
    add constraint stream_head_business_tenant_fk foreign key (tenant_id)
        references platform.business_tenant(tenant_id) not valid;
end if;
end
$ownership_compatibility$;

comment on table platform.business_tenant is
    'RuoYi-backed AI business workspace; credentials and RBAC remain in MySQL RuoYi.';
comment on table platform.ruoyi_user_binding is
    'Deterministic RuoYi user to AI business tenant binding; no credentials are stored.';
