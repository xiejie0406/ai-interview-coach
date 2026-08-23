-- PostgreSQL 业务库边界：不创建账号、密码、角色、权限或 session 表。
create schema if not exists platform;

create table if not exists platform.ruoyi_user_binding (
    ruoyi_user_id bigint primary key,
    business_tenant_id varchar(128) not null,
    status varchar(24) not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint ruoyi_user_binding_status_ck check (status in ('ACTIVE', 'DISABLED'))
);

create table if not exists platform.business_tenant (
    tenant_id varchar(128) primary key,
    owner_ruoyi_user_id bigint not null,
    tenant_type varchar(24) not null default 'PERSONAL',
    status varchar(24) not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    constraint business_tenant_type_ck check (tenant_type in ('PERSONAL', 'WORKSPACE', 'PLATFORM')),
    constraint business_tenant_status_ck check (status in ('ACTIVE', 'DISABLED'))
);

create table if not exists platform.business_membership (
    tenant_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    business_role varchar(48) not null,
    status varchar(24) not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, ruoyi_user_id),
    constraint business_membership_status_ck check (status in ('ACTIVE', 'DISABLED'))
);

comment on table platform.ruoyi_user_binding is
    'Logical binding to MySQL sys_user.user_id; never stores credentials or RBAC facts';
comment on table platform.business_tenant is
    'AI business workspace only; not an authentication or authorization source';
comment on table platform.business_membership is
    'AI collaboration metadata only; RuoYi sys_menu/sys_role remains authorization authority';
