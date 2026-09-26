-- 题库管理工作流兼容迁移：补齐 Question 聚合持久化所需的不可变版本指针。
-- 不修改已发布 V6；已有发布事实优先作为 published 指针的回填来源。

alter table catalog.question
    add column if not exists draft_version_id varchar(128),
    add column if not exists draft_version_no integer,
    add column if not exists draft_content_hash varchar(128),
    add column if not exists published_version_id varchar(128),
    add column if not exists published_version_no integer,
    add column if not exists published_content_hash varchar(128);

-- 若历史写入只填了部分指针，先清空该组字段，再由下方事实回填，避免半成品破坏聚合不变量。
update catalog.question
   set draft_version_id = null, draft_version_no = null, draft_content_hash = null
 where draft_version_id is null
    or draft_version_no is null
    or draft_content_hash is null
    or draft_version_no <= 0
    or btrim(draft_content_hash) = '';
update catalog.question
   set published_version_id = null, published_version_no = null, published_content_hash = null
 where published_version_id is null
    or published_version_no is null
    or published_content_hash is null
    or published_version_no <= 0
    or btrim(published_content_hash) = '';

update catalog.question q
   set draft_version_id = null, draft_version_no = null, draft_content_hash = null
 where draft_version_id is not null
   and not exists (
       select 1
         from catalog.question_version v
        where v.tenant_id = q.tenant_id
          and v.question_id = q.question_id
          and v.question_version_id = q.draft_version_id
          and v.version_no = q.draft_version_no
          and v.content_hash = q.draft_content_hash);
update catalog.question q
   set published_version_id = null, published_version_no = null, published_content_hash = null
 where published_version_id is not null
   and not exists (
       select 1
         from catalog.question_version v
        where v.tenant_id = q.tenant_id
          and v.question_id = q.question_id
          and v.question_version_id = q.published_version_id
          and v.version_no = q.published_version_no
          and v.content_hash = q.published_content_hash);

-- 同一题目可能有多次历史发布；选择最近发布的一次作为当前发布指针。
with latest_publication as (
    select distinct on (p.tenant_id, p.question_id)
           p.tenant_id, p.question_id,
           p.question_version_id, p.question_version_no, p.question_content_hash
      from catalog.question_publication p
      join catalog.question_version v
        on v.tenant_id = p.tenant_id
       and v.question_id = p.question_id
       and v.question_version_id = p.question_version_id
       and v.version_no = p.question_version_no
       and v.content_hash = p.question_content_hash
     order by p.tenant_id, p.question_id, p.published_at desc, p.publication_id desc
)
update catalog.question q
   set published_version_id = p.question_version_id,
       published_version_no = p.question_version_no,
       published_content_hash = p.question_content_hash
  from latest_publication p
 where q.tenant_id = p.tenant_id
   and q.question_id = p.question_id
   and q.published_version_id is null;

-- 仅对明确处于草稿工作流、且尚无草稿指针的旧记录选择最新内容版本。
with latest_draft as (
    select distinct on (tenant_id, question_id)
           tenant_id, question_id,
           question_version_id, version_no, content_hash
      from catalog.question_version
     order by tenant_id, question_id, version_no desc, question_version_id desc
)
update catalog.question q
   set draft_version_id = v.question_version_id,
       draft_version_no = v.version_no,
       draft_content_hash = v.content_hash
  from latest_draft v
 where q.tenant_id = v.tenant_id
   and q.question_id = v.question_id
   and q.draft_version_id is null
   and q.status in ('DRAFT', 'IN_REVIEW', 'PUBLISHED_WITH_DRAFT', 'PUBLISHED_WITH_REVIEW')
   and v.question_version_id is distinct from q.published_version_id;

-- V6 的默认状态是 PUBLISHED；若旧行没有 publication，则不能伪造已发布事实。
update catalog.question
   set status = 'DRAFT'
 where status in ('PUBLISHED', 'PUBLISHED_WITH_DRAFT', 'PUBLISHED_WITH_REVIEW', 'RETIRED')
   and published_version_id is null;

-- V6 默认 PUBLISHED 但没有 publication 的旧记录已经降为 DRAFT；此时再回填其最新内容版本。
with latest_unpublished_draft as (
    select distinct on (tenant_id, question_id)
           tenant_id, question_id,
           question_version_id, version_no, content_hash
      from catalog.question_version
     order by tenant_id, question_id, version_no desc, question_version_id desc
)
update catalog.question q
   set draft_version_id = v.question_version_id,
       draft_version_no = v.version_no,
       draft_content_hash = v.content_hash
  from latest_unpublished_draft v
 where q.tenant_id = v.tenant_id
   and q.question_id = v.question_id
   and q.published_version_id is null
   and q.draft_version_id is null
   and q.status in ('DRAFT', 'IN_REVIEW');

-- 将历史状态归一到当前领域状态机；不凭空制造 publication/review 事实。
update catalog.question
   set status = case
       when published_version_id is null and draft_version_id is null then 'DRAFT'
       when published_version_id is null then
           case when status in ('IN_REVIEW', 'PUBLISHED_WITH_REVIEW') then 'IN_REVIEW' else 'DRAFT' end
       when draft_version_id is null then
           case when status = 'RETIRED' then 'RETIRED' else 'PUBLISHED' end
       else
           case when status = 'RETIRED' then 'RETIRED'
                when status in ('IN_REVIEW', 'PUBLISHED_WITH_REVIEW')
                then 'PUBLISHED_WITH_REVIEW' else 'PUBLISHED_WITH_DRAFT' end
       end;

-- RETIRED 不能保留仍可编辑的草稿指针。
update catalog.question
   set draft_version_id = null, draft_version_no = null, draft_content_hash = null
 where status = 'RETIRED';

alter table catalog.question alter column status set default 'DRAFT';

alter table catalog.question
    add constraint catalog_question_status_ck check (status in (
        'DRAFT', 'IN_REVIEW', 'PUBLISHED',
        'PUBLISHED_WITH_DRAFT', 'PUBLISHED_WITH_REVIEW', 'RETIRED')),
    add constraint catalog_question_aggregate_version_ck check (aggregate_version >= 0),
    add constraint catalog_question_draft_ref_ck check (
        (draft_version_id is null and draft_version_no is null and draft_content_hash is null)
        or (draft_version_id is not null and draft_version_no > 0 and draft_content_hash is not null)),
    add constraint catalog_question_published_ref_ck check (
        (published_version_id is null and published_version_no is null and published_content_hash is null)
        or (published_version_id is not null and published_version_no > 0 and published_content_hash is not null)),
    add constraint catalog_question_state_ref_ck check (
        (status = 'DRAFT' and published_version_id is null)
        or (status = 'IN_REVIEW' and draft_version_id is not null and published_version_id is null)
        or (status = 'PUBLISHED' and draft_version_id is null and published_version_id is not null)
        or (status in ('PUBLISHED_WITH_DRAFT', 'PUBLISHED_WITH_REVIEW')
            and draft_version_id is not null and published_version_id is not null)
        or (status = 'RETIRED' and draft_version_id is null and published_version_id is not null));

create index if not exists catalog_question_admin_page_idx
    on catalog.question (tenant_id, status, question_id);
create index if not exists catalog_question_public_page_idx
    on catalog.question (tenant_id, question_id)
    where status in ('PUBLISHED', 'PUBLISHED_WITH_DRAFT', 'PUBLISHED_WITH_REVIEW');
create index if not exists catalog_rubric_question_version_idx
    on catalog.rubric_version (tenant_id, question_version_id, version_no desc, rubric_version_id desc);

comment on column catalog.question.draft_version_id is
    'Current immutable draft pointer; all id/no/hash fields are null or present together';
comment on column catalog.question.published_version_id is
    'Current immutable published pointer; derived from append-only publication facts during migration';
