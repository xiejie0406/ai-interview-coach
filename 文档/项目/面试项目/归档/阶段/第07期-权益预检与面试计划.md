# 第 07 期：权益预检与面试计划纵切

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-07  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：billing / interview / catalog / frontend  
> 证据结果：NotRun  
> 前置：PHASE-03、04；套餐/用量表达与计划原型批准

## 1. 本期为什么存在

面试开始前必须先确定题目/时间/追问预算并说明预计消耗。用量预检和预留 port 前置，可防止 Agent/语音先发生费用、后补计费语义。

## 2. 用户可见目标

用户选择岗位、级别、主题、时长和文本/语音模式，看到题数/追问/预计用量及可用权益，修改并确认计划；额度不足或无题时不创建会话。

## 3. 技术学习目标

学习确定性 planning、版本快照、Entitlement/UsageReservation、估算与结算分离、多步骤表单和业务拒绝。

## 4. 范围

InterviewSetup/Plan、题目选择策略、时间/追问预算、Free/Pro 测试权益、用量估算、Reservation port、配置/预览/确认/取消 UI。

## 5. 非目标

不实际问答、不接真实支付/价格、不实现完整成本账本、不用 LLM 决定题目发布、额度或状态、不接 JD/简历。

## 6. 前置决策

DEC-008–010、014、043；批准时长/题数/追问模板、用户侧计费单位、测试权益来源和额度不足行为。

## 7. 前置期次和依赖

03 identity/tenant；04 published catalog；06 Provider 成本元数据只作估算输入但不是硬依赖。

## 8. 涉及的 REQ/BR/AC/DES

REQ-03/04/06/14；BR-01/02/09；AC-03/14；DES-MOD-04/09、DES-STATE-PLAN、DES-BILLING-RESERVE。

## 9. 本期完整功能点

配置向导；确定性候选选择；计划版本；题数/时长/追问预算；test entitlement；估算与预留；计划预览/修改/确认/取消；模式不可用/无题/额度不足拒绝。

## 10. 正常流程

选目标与模式→后端检索发布题→生成确定性计划→估算用量/检查权益→用户修改/确认→创建 READY 输入并形成 Reservation。

## 11. 空态、错误、拒绝、取消和恢复

无可用题建议调整筛选；估算失败不允许确认；额度不足显示原因/可选更短方案；语音关闭可改文本；用户取消释放预留；刷新恢复 Draft 计划；过期内容/价格版本要求重新确认。

## 12. 后端模块

interview domain `InterviewSetup/InterviewPlan/TopicAllocation/PlanPolicy`；billing domain `Entitlement/UsageEstimate/UsageReservation`；application `CreatePlan/ConfirmPlan/CheckEntitlement/ReserveUsage`。

## 13. 前端页面和组件

`/app/interviews/new`、`/app/interviews/plans/:id`、`/app/usage`；`InterviewSetupWizard`、`PlanPreview`、`UsageEstimate`、`EntitlementNotice`、`UnsavedChangesDialog`。

## 14. 数据实体、约束和迁移

`interview.plan_draft/plan_version/plan_question`；`billing.plan/entitlement/usage_reservation`。确认版本不可变；只引用发布内容版本；reservation 幂等、expiresAt、tenant scope；测试权益标 source。

## 15. REST/SSE/WebSocket/API 或事件

`POST /interview-plans`、`PUT /{id}`、`POST /{id}/confirm|cancel`、`GET /usage/estimate|entitlements`；错误 `INSUFFICIENT_CATALOG/USAGE_EXCEEDED/PRICE_VERSION_EXPIRED`；不用 SSE/WS。

## 16. Agent/Prompt/Provider

可定义 `InterviewPlannerPort` 候选但 P0 使用确定性策略；任何 LLM 计划必须经 `PlanPolicy` 校验且本期不启用。

## 17. 安全与隐私

计划与目标私有；用量页面仅本人；金额/额度确定性复算；不向 Provider 发数据；防参数篡改/并发透支。

## 18. 计划新增文件树

```text
interview-domain/.../interview/{InterviewSetup,InterviewPlan,TopicAllocation,PlanPolicy}.java
interview-domain/.../billing/{Plan,Entitlement,UsageReservation}.java
interview-application/.../{interview/plan,billing/entitlement}/
interview-adapters/.../persistence/{interview,billing}/; inbound/rest/{plan,usage}/
interview-boot/.../db/migration/V###__create_plan_entitlement_reservation.sql
frontend/src/features/interview/setup/; frontend/src/features/billing/usage/
contracts/openapi/{interview-plan,entitlement}.yaml
```

## 19. 计划修改文件树

```text
frontend/src/app/router.tsx
frontend/src/features/catalog/api/
interview-application/.../catalog/PublishedQuestionQuery.java
contracts/openapi/common.yaml
```

## 20. 后续 TASK 拆分建议

计划规则、权益/预留、迁移、API、向导 UI、并发/拒绝验证；真实价格/支付明确排除。

## 21. 验证建议

单元：预算/预留状态；集成：并发/过期/内容版本；契约：拒绝错误；UI：多步骤/空态/取消；Golden Set：NotApplicable；UAT：确认短文本计划和额度不足。不能证明真实收费。

## 22. 明确完成标准

计划可复现且只引用发布版本；确认前显示预计用量；无题/额度不足不建 READY；重复确认/取消幂等；预留可释放。

## 23. 本期不能证明什么

不能证明面试问答、实际 Provider 成本、支付、退款或用户愿意付费。

## 24. 风险与停止条件

计划规则未批准、估算与用户展示单位不一致、并发可透支、LLM 可改额度/状态或 JD/简历被暗中引入时停止。

## 25. 下一期进入条件

Confirmed Plan 和 Reservation 成为 Session 的稳定输入；取消/过期语义清楚。

## 26. 建议学习和复盘内容

复盘 domain service、deterministic planning、reservation pattern、business rejection、estimate vs settlement 和多步骤 UX。
