-- 题库 V2：将 Portal 的固定模块从 target_roles 中独立出来。
-- GENERAL 只表示无法追溯模块的历史行；新建/新版本由应用层拒绝 GENERAL。

alter table catalog.question_version
    add column if not exists category varchar(96);

-- 不根据 target_roles 猜测模块，无法可靠回溯的历史数据显式标为 GENERAL。
-- 历史版本由不可变触发器保护；本次仅回填新增分类列，临时关闭用户触发器并在回填后恢复。
alter table catalog.question_version disable trigger user;

update catalog.question_version
   set category = 'GENERAL'
 where category is null
    or btrim(category) = ''
    or category not in (
        'GENERAL',
        'AGENT_BASICS_DEEP_V3',
        'LLM_FOUNDATION_DEEP_V3',
        'PROMPT_ENGINEERING_DEEP_V3',
        'RAG_DEEP_V3',
        'KNOWLEDGE_BASE_DEEP_V3',
        'WORKFLOW_DEEP_V3',
        'TOOL_CALLING_DEEP_V3',
        'MEMORY_DEEP_V3',
        'MULTI_AGENT_DEEP_V3',
        'EVALUATION_DEEP_V3',
        'AGENT_SECURITY_DEEP_V3',
        'AGENT_ENGINEERING_DEEP_V3');

alter table catalog.question_version enable trigger user;

alter table catalog.question_version
    alter column category set not null;

alter table catalog.question_version
    add constraint catalog_question_version_category_ck check (category in (
        'GENERAL',
        'AGENT_BASICS_DEEP_V3',
        'LLM_FOUNDATION_DEEP_V3',
        'PROMPT_ENGINEERING_DEEP_V3',
        'RAG_DEEP_V3',
        'KNOWLEDGE_BASE_DEEP_V3',
        'WORKFLOW_DEEP_V3',
        'TOOL_CALLING_DEEP_V3',
        'MEMORY_DEEP_V3',
        'MULTI_AGENT_DEEP_V3',
        'EVALUATION_DEEP_V3',
        'AGENT_SECURITY_DEEP_V3',
        'AGENT_ENGINEERING_DEEP_V3'));

create index if not exists catalog_question_version_category_idx
    on catalog.question_version (tenant_id, category, question_id, version_no desc, question_version_id desc);

comment on column catalog.question_version.category is
    'Stable Portal module code; GENERAL is migration-only legacy fallback and is not accepted for new writes';
