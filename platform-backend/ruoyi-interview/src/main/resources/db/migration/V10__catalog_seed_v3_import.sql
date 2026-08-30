-- 将已有的若依迁移题库收口为 V2 首批 606 条正式公开题目。
-- 仅处理 agent-deep-v3 的 600 条和 6 条基础题；其余历史题继续保留为 GENERAL。

alter table catalog.question_version disable trigger user;

update catalog.question_version v
   set category = split_part(q.stable_key, '-', 4) || '_DEEP_V3'
  from catalog.question q
 where q.tenant_id = v.tenant_id
   and q.published_version_id = v.question_version_id
   and q.stable_key like 'agent-deep-v3-%';

update catalog.question_version v
   set category = case q.stable_key
       when 'java-concurrency-thread-pool' then 'AGENT_ENGINEERING_DEEP_V3'
       when 'java-jvm-memory' then 'AGENT_ENGINEERING_DEEP_V3'
       when 'rag-retrieval-quality' then 'RAG_DEEP_V3'
       when 'agent-tool-safety' then 'TOOL_CALLING_DEEP_V3'
       when 'llm-structured-output' then 'LLM_FOUNDATION_DEEP_V3'
       when 'agent-memory-boundary' then 'MEMORY_DEEP_V3'
       else category
   end
  from catalog.question q
 where q.tenant_id = v.tenant_id
   and q.published_version_id = v.question_version_id
   and q.stable_key in (
       'java-concurrency-thread-pool', 'java-jvm-memory', 'rag-retrieval-quality',
       'agent-tool-safety', 'llm-structured-output', 'agent-memory-boundary');

alter table catalog.question_version enable trigger user;

-- 旧 seed 已把来源快照写入 question_version；这里建立服务端来源 registry，
-- 使发布/回读可以验证同一 source version，而不是信任客户端携带的 verified 标记。
insert into catalog.content_source (
    tenant_id, content_source_id, stable_key, status, current_version_id,
    aggregate_version, created_at, updated_at
)
select distinct v.tenant_id,
       v.source_id,
       'legacy-seed-' || v.source_id,
       'DRAFT',
       null,
       1,
       coalesce(v.created_at, current_timestamp),
       current_timestamp
  from catalog.question q
  join catalog.question_version v
    on v.tenant_id = q.tenant_id and v.question_version_id = q.published_version_id
 where (q.stable_key like 'agent-deep-v3-%'
        or q.stable_key in (
            'java-concurrency-thread-pool', 'java-jvm-memory', 'rag-retrieval-quality',
            'agent-tool-safety', 'llm-structured-output', 'agent-memory-boundary'))
   and v.source_id is not null
   and v.source_version_id is not null
 on conflict (tenant_id, content_source_id) do nothing;

insert into catalog.content_source_version (
    tenant_id, content_source_version_id, content_source_id, version_no,
    status, source_type, title, source_url, publisher, license_code,
    accessed_at, attribution_text, content_hash, evidence_hash,
    verification_fact_id, verified_at, created_at
)
select distinct v.tenant_id,
       v.source_version_id,
       v.source_id,
       1,
       'VERIFIED',
       'SELF_AUTHORED_SEED',
       'RuoYi V2 self-authored catalog seed',
       null,
       'AI Interview Coach',
       v.source_license_code,
       coalesce(v.source_verified_at, v.created_at, current_timestamp),
       'Migrated from the legacy self-authored seed; source hash retained.',
       v.source_content_hash,
       v.source_content_hash,
       v.source_verification_fact_id,
       coalesce(v.source_verified_at, v.created_at, current_timestamp),
       coalesce(v.created_at, current_timestamp)
  from catalog.question q
  join catalog.question_version v
    on v.tenant_id = q.tenant_id and v.question_version_id = q.published_version_id
 where (q.stable_key like 'agent-deep-v3-%'
        or q.stable_key in (
            'java-concurrency-thread-pool', 'java-jvm-memory', 'rag-retrieval-quality',
            'agent-tool-safety', 'llm-structured-output', 'agent-memory-boundary'))
   and v.source_id is not null
   and v.source_version_id is not null
 on conflict (tenant_id, content_source_version_id) do nothing;

update catalog.content_source s
   set current_version_id = v.content_source_version_id,
       status = 'VERIFIED',
       aggregate_version = greatest(s.aggregate_version, 1),
       updated_at = current_timestamp
  from catalog.content_source_version v
 where v.tenant_id = s.tenant_id
   and v.content_source_id = s.content_source_id
   and v.status = 'VERIFIED'
   and s.content_source_id in (
       select distinct qv.source_id
         from catalog.question q
         join catalog.question_version qv
           on qv.tenant_id = q.tenant_id and qv.question_version_id = q.published_version_id
        where q.stable_key like 'agent-deep-v3-%'
           or q.stable_key in (
               'java-concurrency-thread-pool', 'java-jvm-memory', 'rag-retrieval-quality',
               'agent-tool-safety', 'llm-structured-output', 'agent-memory-boundary'));
