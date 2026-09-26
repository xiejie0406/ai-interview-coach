-- Aden V1: Workspace 与成员边界。
-- UUID 使用小写文本并以 ASCII binary collation 比较；所有时间均由应用按 UTC 写入 DATETIME(6)。

create table aden_workspace (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    workspace_name varchar(128) not null,
    workspace_status varchar(24) character set ascii collate ascii_bin not null,
    last_event_seq bigint not null default 0,
    version bigint not null default 0,
    created_by_ruoyi_user_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id),
    constraint ck_aden_workspace_status check (workspace_status in ('ACTIVE', 'SUSPENDED')),
    constraint ck_aden_workspace_event_seq check (last_event_seq >= 0),
    constraint ck_aden_workspace_version check (version >= 0),
    index ix_aden_workspace_status (workspace_status, updated_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Aden workspace authority boundary';

create table aden_workspace_member (
    workspace_id char(36) character set ascii collate ascii_bin not null,
    ruoyi_user_id bigint not null,
    workspace_role varchar(16) character set ascii collate ascii_bin not null,
    member_status varchar(16) character set ascii collate ascii_bin not null,
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (workspace_id, ruoyi_user_id),
    constraint fk_aden_member_workspace foreign key (workspace_id)
        references aden_workspace (workspace_id) on delete restrict on update restrict,
    constraint ck_aden_member_role check (workspace_role in ('OWNER', 'OPERATOR', 'VIEWER')),
    constraint ck_aden_member_status check (member_status in ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    constraint ck_aden_member_version check (version >= 0),
    index ix_aden_member_user (ruoyi_user_id, member_status, workspace_id),
    index ix_aden_member_workspace_role (workspace_id, member_status, workspace_role)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='RuoYi user membership in Aden workspace';
