# 第 04 期：版本化题库与内容发布纵切

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-04  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：catalog / content governance / frontend  
> 证据结果：NotRun  
> 前置：PHASE-01；内容来源、角色和 P0 范围批准

## 1. 本期为什么存在

题目、来源、答案要点、追问和 Rubric 的版本是面试与评测的权威输入。没有先建立可发布、不可静默覆盖的内容纵切，Agent 和报告无法复现。

## 2. 用户可见目标

内容管理员可起草、审核、发布少量自研题；用户可按方向、难度、岗位/级别和标签筛选并打开已发布题目，空结果可恢复。

## 3. 技术学习目标

学习聚合/不可变版本、内容工作流、全文检索、管理 RBAC、版权元数据、乐观锁和历史引用。

## 4. 范围

Taxonomy、Question、QuestionVersion、RubricVersion、Source、Draft/Review/Publish/Retire、用户搜索/详情、最小管理后台、发布审计与许可种子内容。

## 5. 非目标

不抓取商业题库、不让 LLM 自动发布、不做用户作答/评分、不引入 pgvector/Elasticsearch、不追求题量。

## 6. 前置决策

DEC-007、015、037、040、内容管理员角色和来源许可政策；PRD 截断修复后才可形成 tasks。

## 7. 前置期次和依赖

依赖 01；管理身份可先用批准 fake/seed role，真实管理端与 03 principal 集成后才能对外使用。

## 8. 涉及的 REQ/BR/AC/DES

REQ-01/02/06；BR-01/02/11/12；AC-01/11；DES-MOD-02、DES-DATA-CATALOG、DES-API-CATALOG。

## 9. 本期完整功能点

分类/标签/岗位模型；题目草稿与来源；答案点/误区/追问/Rubric；审核发布/下线；历史版本；用户筛选/分页/详情；管理员版本比较与发布拒绝。

## 10. 正常流程

管理员创建草稿→补来源/Rubric→提交审核→批准发布不可变版本→用户筛选并打开→后续引用 version ID。

## 11. 空态、错误、拒绝、取消和恢复

无题/筛选空显示重置；来源或 Rubric 缺失拒绝发布；普通用户看不到草稿；并发编辑冲突提示刷新；管理员取消草稿不影响已发布版；下线后历史引用仍能内部回读。

## 12. 后端模块

domain `Question/QuestionVersion/RubricVersion/ContentSource/PublicationPolicy`；application `DraftQuestion/SubmitReview/PublishQuestion/SearchPublishedQuestions`；persistence catalog；inbound user/admin REST。

## 13. 前端页面和组件

`/questions`、`/questions/:id`、`/admin/catalog`、`/admin/catalog/:id/edit`、`/review`；`QuestionFilters`、`QuestionCard`、`VersionDiff`、`PublicationChecklist`、空/拒绝组件。

## 14. 数据实体、约束和迁移

`catalog.question`、`question_version`、`rubric_version`、`taxonomy`、`question_taxonomy`、`content_source`、`publication`。发布版本不可更新；source URL/license/accessedAt 必填；status/version 乐观锁；公共内容明确 visibility。

## 15. REST/SSE/WebSocket/API 或事件

`GET /questions`、`GET /questions/{id}`；admin draft/review/publish/retire 命令均幂等/版本检查；事件 `catalog.question.published|retired`。不用 SSE/WS。

## 16. Agent/Prompt/Provider

无生产 Agent。可预留内容建议工具为未来范围，但任何模型生成只能是草稿且不进入本期。

## 17. 安全与隐私

内容管理员最小权限；发布审计；种子内容许可；不读取用户私密数据；富文本/Markdown 输出编码，防存储型 XSS。

## 18. 计划新增文件树

```text
interview-domain/.../catalog/{Question,QuestionVersion,RubricVersion,ContentSource,PublicationPolicy}.java
interview-application/.../catalog/{DraftQuestion,PublishQuestion,SearchPublishedQuestions}.java
interview-adapters/.../{persistence/catalog,inbound/rest/catalog,inbound/rest/admincatalog}/
interview-boot/.../db/migration/V###__create_catalog.sql
frontend/src/features/catalog/{pages,components,hooks,api}/
frontend/src/features/admin/catalog/{pages,components}/
contracts/openapi/{catalog,admin-catalog}.yaml
content/seed/README.md
```

## 19. 计划修改文件树

```text
frontend/src/app/router.tsx
interview-boot/.../SecurityConfiguration.java
contracts/asyncapi/common.yaml
docs/reference/data-ownership.md
```

## 20. 后续 TASK 拆分建议

模型/迁移、内容工作流、用户查询、管理 API/UI、种子/许可、版本/越权验证六组；种子题数量和 owner 在 tasks 明确。

## 21. 验证建议

单元：PublicationPolicy；集成：版本/tenant/全文筛选；契约：分页/错误；UI：筛选空态与审核拒绝；Golden Set：内容 fixture 基础，不评模型；UAT：管理员发布和用户检索。版权仍需独立复核。

## 22. 明确完成标准

已发布版本不可静默覆盖；旧版可按内部引用回读；普通用户只见发布内容；组合筛选和空态可观察；所有发布内容有来源/Rubric owner。

## 23. 本期不能证明什么

不能证明题库规模/质量壁垒、AI 评分、用户学习效果或内容法律意见。

## 24. 风险与停止条件

上游截断未修复、许可不明、无人工 Rubric owner、发布版本可被 UPDATE、管理员能查看用户回答时停止。

## 25. 下一期进入条件

稳定的已发布 `QuestionVersion/RubricVersion` 可被 Practice/Interview/Evaluation 只读引用。

## 26. 建议学习和复盘内容

复盘 content lifecycle、immutable versioning、optimistic locking、PostgreSQL FTS、RBAC 与版权来源治理。
