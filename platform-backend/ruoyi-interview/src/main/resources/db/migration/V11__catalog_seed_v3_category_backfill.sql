-- 修复首批 606 条公开题目的模块分类。
-- V10 的来源 registry 已存在，但部分环境只完成了来源写入，未完成 category 回填；
-- 仅补齐明确可追溯的 agent-deep-v3 600 条和 6 条基础题，其余历史数据继续保持 GENERAL。

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
