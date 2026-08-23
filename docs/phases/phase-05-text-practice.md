# 第 05 期：文本单题练习纵切

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-05  
> 风险等级：L2  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：practice / frontend  
> 证据结果：NotRun  
> 前置：PHASE-03、04

## 1. 本期为什么存在

这是第一条真实学习纵切：在引入模型前先证明题目→回答事实→历史/重练的确定性业务链，避免把用户回答生命周期和 AI 评分耦合。

## 2. 用户可见目标

用户打开题目、保存草稿、提交文本回答、查看自己的历史版本、重答并标记待加强/收藏；未接 AI 时明确显示“反馈尚不可用”。

## 3. 技术学习目标

学习 tenant aggregate、草稿/提交状态、幂等提交、不可变 AnswerVersion、前端表单恢复和服务端状态管理。

## 4. 范围

PracticeAttempt、AnswerVersion、草稿、提交、重练、收藏、待加强、历史、题单最小进度、评测请求 port/outbox 占位。

## 5. 非目标

不输出 LLM 分数、不做语音、不自动判错、不生成长期 LearningPlan、不实现高级题单推荐。

## 6. 前置决策

“先答后看”、草稿保留、重答历史、用户手动待加强的产品语义；Identity/Catalog 契约批准。

## 7. 前置期次和依赖

03 principal/tenant；04 已发布题目/Rubric 版本。不得直接访问 catalog Repository，只通过公开查询/版本引用。

## 8. 涉及的 REQ/BR/AC/DES

REQ-01/06/10，REQ-07 的输入基础；AC-01/02 输入；BR-01/11；DES-MOD-03、DES-DATA-PRACTICE。

## 9. 本期完整功能点

创建 attempt、保存草稿、提交、重复提交幂等、重答新版本、历史列表、收藏/待加强、题目下线后的历史提示、pending/unavailable 反馈状态。

## 10. 正常流程

筛选题目→打开详情→创建 attempt→保存/提交→显示回答和参考要点→标记待加强或重答→查看历史。

## 11. 空态、错误、拒绝、取消和恢复

无历史为空态；题目下线拒绝新 attempt 但保留历史；内容版本过期提示刷新；重复提交返回同一事实；未保存离开确认；断线恢复草稿；取消新练习不创建提交版本。

## 12. 后端模块

domain `PracticeAttempt/AnswerVersion/PracticeProgress/PracticePolicy`；application `StartPractice/SaveDraft/SubmitAnswer/RepeatAnswer/ListHistory/MarkForReview`；practice persistence/API。

## 13. 前端页面和组件

`/questions/:id/practice`、`/app/practice/history`、`/app/practice/review`；`AnswerEditor`、`DraftStatus`、`AttemptHistory`、`ReferencePoints`、`FeedbackUnavailable`；query hooks + short-lived draft store。

## 14. 数据实体、约束和迁移

`practice.attempt`、`answer_version`、`bookmark`、`review_mark`、`practice_progress`；tenant_id；`(tenant_id,idempotency_key)` 唯一；submitted AnswerVersion 不可变；引用 question/rubric version ID。

## 15. REST/SSE/WebSocket/API 或事件

`POST /practice-attempts`、`PUT /{id}/draft`、`POST /{id}/submit`、`POST /{id}/repeat`、`GET /history`、bookmark/review commands；事件 `practice.answer.submitted`。不用 SSE/WS。

## 16. Agent/Prompt/Provider

无实际模型；`EvaluationRequestPort` 仅发布不可变 AnswerVersion reference。UI 不用假分数填充。

## 17. 安全与隐私

回答为 Confidential；严格 tenant owner；日志不记录正文；草稿 IndexedDB 若批准则按 user/tenant namespace、TTL、logout 清除；不进入运营视图。

## 18. 计划新增文件树

```text
interview-domain/.../practice/{PracticeAttempt,AnswerVersion,PracticeProgress,PracticePolicy}.java
interview-application/.../practice/{StartPractice,SaveDraft,SubmitAnswer,ListHistory}.java
interview-adapters/.../{persistence/practice,inbound/rest/practice}/
interview-boot/.../db/migration/V###__create_practice.sql
frontend/src/features/practice/{pages,components,hooks,api,store}/
contracts/openapi/practice.yaml
```

## 19. 计划修改文件树

```text
frontend/src/features/catalog/pages/QuestionDetailPage.tsx
frontend/src/app/router.tsx
contracts/asyncapi/common.yaml
interview-application/.../catalog/PublishedQuestionQuery.java
```

## 20. 后续 TASK 拆分建议

领域/迁移、草稿/提交、历史/标记、API、UI/恢复、tenant/幂等验证；Evaluation port 只写契约，不实现模型。

## 21. 验证建议

单元：状态/重复提交；集成：tenant/版本/唯一键；契约：过期/下线错误；UI：草稿、离开、空态；Golden Set：NotApplicable；UAT：完成一次文本练习和重答。

## 22. 明确完成标准

用户可完成纵切；已提交版本不可覆盖；断线/重复提交不丢/不重；跨 tenant 拒绝；未接评测时明确 unavailable。

## 23. 本期不能证明什么

不能证明回答正确、AI 反馈、错题自动识别、学习提升或面试能力。

## 24. 风险与停止条件

“错题”需模型才能定义时必须退化为用户“待加强”；若 AnswerVersion 可修改、跨 tenant 或日志泄正文立即停止。

## 25. 下一期进入条件

稳定 AnswerVersion 与 evaluation request 契约可供 Phase 06 回放；Catalog/Practice owner 边界无跨 Repository。

## 26. 建议学习和复盘内容

复盘 CQRS-lite、idempotency、immutable input、offline-first 草稿边界，以及 AI 前先把业务事实做对。
