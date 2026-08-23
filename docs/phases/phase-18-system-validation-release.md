# 第 18 期：系统审查、浏览器 UAT 与上线就绪

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-18  
> 风险等级：L3  
> 产出/适用阶段：8–10 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：quality / product / security / release  
> 证据结果：NotRun  
> 上线就绪结论：NotAssessed  
> 发布事实：NotReleased  
> 前置：PHASE-01–17 范围内实现/局部 EV；审查、验证、UAT、上线准备分别授权

## 1. 本期为什么存在

各期局部证据不能自动推导系统闭环。最后一期独立审查批准范围，执行 AC—EV、Golden Set、浏览器 UAT、故障/安全/隐私/容量与恢复证据，形成是否 `ReleaseReady` 的输入。

## 2. 用户可见目标

目标用户按真实浏览器旅程完成题库、文本/语音面试、报告复练、额度/支付和隐私删除，并由用户/业务 owner 逐场景形成接受或退回决定。

## 3. 技术学习目标

学习 risk-based testing、AC—EV traceability、Playwright UAT、Golden quality gate、fault injection、performance evidence、release readiness 与独立授权。

## 4. 范围

只读审查、静态/单元/集成/契约/UI/真实 Provider 最小验证、Golden Set、安全/tenant/隐私、故障/容量、备份恢复、迁移/冒烟/回滚准备、用户 UAT、残余风险。

## 5. 非目标

不代替用户验收、不自动修复/扩大范围、不执行实际发布/Git、不纳入 Realtime/B2B/K8s/微服务/SSO/视频数字人。

## 6. 前置决策

批准候选版本、验证命令/环境/数据/账号/费用、UAT 场景/操作者、SLO/质量门、残余风险/例外、上线准备与回滚演练范围。

## 7. 前置期次和依赖

01–17 范围内实现与局部 evidence；P0 Feature Spec/设计/tasks Approved；任何未实现/NotRun/Blocked 显式列出。

## 8. 涉及的 REQ/BR/AC/DES

当前 PRD REQ-01–15、BR-01–12、AC-01–14 及批准 DES/TASK。P0 AC-01–12 必须有结果；P1 只按批准 T2 范围，不把 REQ-13 延后项算缺陷。

## 9. 本期完整功能点

范围/差异审查；模块边界；AC—EV；Golden regression；浏览器主/异常旅程；Provider/支付最小真实链路；tenant/CSRF/CORS/log；删除/备份；故障/容量；UAT；上线就绪/残余风险/停止/回滚。

## 10. 正常流程

锁候选→独立审查→执行批准分层验证→修复需退回相应阶段而非本期暗改→用户 UAT→评审迁移/监控/备份/回滚→记录 NotReady 或 ReleaseReady+NotReleased。

## 11. 空态、错误、拒绝、取消和恢复

至少验证无题/新用户/无报告；LLM/ASR/TTS/支付/对象故障；跨 tenant/无权限/麦克风拒绝/额度不足；用户取消/主动结束；SSE/WS/worker/Redis/实例恢复；删除 PARTIAL_FAILED。

## 12. 后端模块

审查全部 module/package/依赖、schema owner、事务/幂等/状态/权限；本期不新建生产业务 module，验证资产归 test/e2e/security/performance 与 Feature evidence。

## 13. 前端页面和组件

覆盖 public/auth/catalog/practice/interview/report/learning/voice/billing/privacy/admin/status；检查正常、空、加载、拒绝、错误、过期、取消、断线/恢复、键盘/焦点/标签/对比度/窄屏。

## 14. 数据实体、约束和迁移

审查所有 schema owner/tenant/版本/唯一键/审计/retention；在批准环境验证从空库升级和批准基线升级、备份恢复/PITR、对象 TTL；不在 Phase 复制 migration 结果。

## 15. REST/SSE/WebSocket/API 或事件

OpenAPI/AsyncAPI/schema compatibility、auth/error/idempotency、SSE replay/terminal、WS sequence/backpressure/cancel、Webhook sign/replay/order；未知事件安全处理。

## 16. Agent/Prompt/Provider

Golden Set 区分链路/内容质量；验证主备/Flag/版本/成本/拒判；真实 Provider 最小授权样本；Prompt Injection/坏 schema；Composer/Learning 不越界。

## 17. 安全与隐私

tenant 越权、session/CSRF/CORS、上传、admin MFA/reason/audit、Secret/log/trace、同意前零采集、撤回、删除/备份/外部副本；P0 安全缺陷阻断。

## 18. 计划新增文件树

```text
docs/features/<FEAT-ID>-<slug>/{verification,acceptance,release-readiness}.md
docs/releases/<candidate-id>/{README,smoke-plan,rollback-plan,release-notes}.md
e2e/{auth,catalog,practice,text-interview,voice-fallback,report-learning,billing-privacy}/*.spec.ts
security/{tenant,csrf-cors,upload,log-redaction}/*.md
performance/{sse,websocket,job-backlog}/
benchmarks/reports/<candidate-id>/
```

## 19. 计划修改文件树

```text
仅在缺陷退回相应 Phase/Feature tasks 后修改生产文件
docs/phases/review-checklist.md                  # 只汇总链接/结果口径
docs/releases/<candidate-id>/README.md           # 汇总，不复制 EV/UAT 正文
```

## 20. 后续 TASK 拆分建议

候选锁定/审查、AC—EV 分层、Golden、browser UAT、security/privacy、fault/performance、backup/migration、release readiness 分包；每包精确命令/影响/停止/授权。

## 21. 验证建议

单元/集成/契约/UI/Golden/UAT 全部适用；真实外部链路、性能、故障、恢复各单独授权。Mock/fake、截图、启动成功和单次 demo 都不能单独证明业务交付。

## 22. 明确完成标准

阻断审查关闭或有批准例外；P0 AC 有真实结果；用户完成 UAT 决定；迁移/备份/监控/停止/回滚可审；满足才记录 `ReleaseReady`，同时仍 `NotReleased`。

## 23. 本期不能证明什么

即使 ReleaseReady，也不能证明已发布、长期 SLO、市场成功、学习/就业结果或未测试环境；Agent 不能替用户形成 UAT。

## 24. 风险与停止条件

任一 P0 AC Fail、跨租户/隐私/支付账本缺陷、无界成本、不可恢复数据、Golden 门失败或证据/授权不足均停止上线就绪；按根因退回阶段 1/3/4/5/7。

## 25. 下一期进入条件

本路线无第 19 期。若 NotReady，按缺陷退回相应 Feature；若 ReleaseReady，实际发布仍需用户明确授权环境/版本/步骤，Git 各动作仍分别授权。

## 26. 建议学习和复盘内容

复盘 testing pyramid/diamond、contract/e2e trade-off、quality vs acceptance、evidence limits、incident/rollback readiness 与 ReleaseReady≠Released。
