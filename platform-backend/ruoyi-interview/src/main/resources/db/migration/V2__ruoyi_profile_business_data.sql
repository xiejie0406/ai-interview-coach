-- 面试档案是 AI 业务扩展，不是第二套身份表；认证/RBAC 仍由 MySQL RuoYi 管理。
create schema if not exists platform;

create table if not exists platform.profile_version (
    business_tenant_id varchar(128) not null,
    ruoyi_user_id bigint not null,
    profile_version_id varchar(128) not null,
    version_no integer not null,
    content_hash varchar(128) not null,
    profile_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    primary key (business_tenant_id, profile_version_id, version_no),
    unique (business_tenant_id, ruoyi_user_id, profile_version_id, version_no)
);

comment on table platform.profile_version is
    'AI 面试档案扩展；ruoyi_user_id 引用 MySQL sys_user.user_id，绝不保存凭据或 RBAC。';
