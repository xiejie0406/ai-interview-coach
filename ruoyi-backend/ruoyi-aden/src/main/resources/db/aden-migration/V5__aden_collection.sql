-- 真实用户发起的详情采集；不向合成 Runner 投递任务。
alter table aden_task drop check ck_aden_task_type;
alter table aden_task drop check ck_aden_task_capability;
alter table aden_task add constraint ck_aden_task_type check (task_type in ('SYNTHETIC_CORE','JD_DETAIL_CAPTURE','MANUAL_COLLECTION_ENTRY'));
alter table aden_task add constraint ck_aden_task_capability check ((task_type='SYNTHETIC_CORE' and required_capability='CORE') or (task_type in ('JD_DETAIL_CAPTURE','MANUAL_COLLECTION_ENTRY') and required_capability='COL'));

create table aden_collection_item (
 workspace_id char(36) character set ascii collate ascii_bin not null,
 item_id char(36) character set ascii collate ascii_bin not null,
 identity_key varchar(128) character set ascii collate ascii_bin not null,
 platform varchar(16) not null,
 sku varchar(64) null,
 title varchar(500) not null,
 snapshot_id char(36) character set ascii collate ascii_bin not null,
 generation int not null default 1,
 deleted boolean not null default false,
 version bigint not null default 1,
 created_at datetime(6) not null,
 updated_at datetime(6) not null,
 primary key(workspace_id,item_id),
 unique key uq_aden_collection_identity(workspace_id,identity_key),
 foreign key(workspace_id) references aden_workspace(workspace_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
create table aden_collection_snapshot (
 workspace_id char(36) character set ascii collate ascii_bin not null,
 snapshot_id char(36) character set ascii collate ascii_bin not null,
 item_id char(36) character set ascii collate ascii_bin not null,
 core_task_id char(36) character set ascii collate ascii_bin not null,
 request_hash char(64) not null,
 actor_id bigint not null,
 generation int not null,
 source varchar(16) not null,
 content_json json not null,
 manifest_json json not null,
 manifest_version int not null default 1,
 status varchar(24) not null,
 created_at datetime(6) not null,
 primary key(workspace_id,snapshot_id),
 foreign key(workspace_id,item_id) references aden_collection_item(workspace_id,item_id),
 foreign key(workspace_id,core_task_id) references aden_task(workspace_id,task_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
create table aden_collection_manifest (
 workspace_id char(36) character set ascii collate ascii_bin not null,
 snapshot_id char(36) character set ascii collate ascii_bin not null,
 version int not null,
 manifest_json json not null,
 created_at datetime(6) not null,
 primary key(workspace_id,snapshot_id,version),
 foreign key(workspace_id,snapshot_id) references aden_collection_snapshot(workspace_id,snapshot_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
create table aden_collection_curation (
 workspace_id char(36) character set ascii collate ascii_bin not null,
 item_id char(36) character set ascii collate ascii_bin not null,
 revision bigint not null,
 content_json json not null,
 created_at datetime(6) not null,
 primary key(workspace_id,item_id,revision),
 foreign key(workspace_id,item_id) references aden_collection_item(workspace_id,item_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
create table aden_collection_upload (
 workspace_id char(36) character set ascii collate ascii_bin not null,
 snapshot_id char(36) character set ascii collate ascii_bin not null,
 image_id varchar(128) character set ascii collate ascii_bin not null,
 asset_id char(36) character set ascii collate ascii_bin not null,
 total_size bigint not null,
 received_size bigint not null default 0,
 sha256 char(64) character set ascii collate ascii_bin not null,
 mime_type varchar(64) not null,
 complete boolean not null default false,
 primary key(workspace_id,snapshot_id,image_id),
 unique key uq_aden_collection_asset(workspace_id,asset_id),
 foreign key(workspace_id,snapshot_id) references aden_collection_snapshot(workspace_id,snapshot_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
create table aden_collection_export (
 workspace_id char(36) character set ascii collate ascii_bin not null,
 export_id char(36) character set ascii collate ascii_bin not null,
 selection_json json not null,
 file_name varchar(100) not null,
 mime_type varchar(100) not null,
 byte_size bigint not null,
 created_at datetime(6) not null,
 primary key(workspace_id,export_id),
 foreign key(workspace_id) references aden_workspace(workspace_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
