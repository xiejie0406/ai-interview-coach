# 第 15 期：完整隐私删除、管理员权限与审计

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-15  
> 风险等级：L3  
> 产出/适用阶段：5–9 隐私专项前瞻  
> 阶段状态：WaitingForApproval  
> owner：governance / security / operations / frontend  
> 证据结果：NotRun  
> 前置：PHASE-03、08、12、14；隐私/备份/管理员决策批准

## 1. 本期为什么存在

第 03/12 期只建立同意与音频最小生命周期。本期把所有业务对象、外部副本、导出/注销、管理员敏感访问和审计串成完整 L3 治理闭环，作为真实支付/生产候选前门。

## 2. 用户可见目标

用户可查看数据类别/保留、导出、逐项删除、撤回语音用途和注销，看到处理中/部分失败/完成/法律阻塞；管理员敏感访问有理由与审计。

## 3. 技术学习目标

学习 data inventory、retention owner、deletion saga、backup semantics、MFA/reason code、append-only audit、log redaction 和 privacy UAT。

## 4. 范围

RetentionPolicy、DataInventory、ExportRequest、DeletionRequest/Saga、账号注销、Provider/object/DB/backup 状态、Admin RBAC/MFA 候选、reason code、审计查询、日志红线、威胁模型。

## 5. 非目标

不做 B2B 组织隐私、不承诺瞬时清除备份、不让审计保存正文、不形成法律意见、不恢复已物理删除数据。

## 6. 前置决策

DEC-045–050、049；各数据保留/删除/备份窗口、Provider 删除能力、legal hold、管理员 MFA/角色、导出格式/身份确认。

## 7. 前置期次和依赖

03 identity/consent；08 Job/outbox；12 audio lifecycle；14 resilience/audit flag。覆盖 04–13 所有数据 owner。

## 8. 涉及的 REQ/BR/AC/DES

REQ-11/12；BR-06/07/10/12；AC-04/10/12；DES-GOV-CONSENT/DELETE/AUDIT、DES-SEC-ADMIN。

## 9. 本期完整功能点

数据清单；retention policy；导出 Job；逐对象/会话/账号删除；同步隐藏+异步删除；Provider/object/backup 状态；撤回阻断新处理；管理员 role/MFA/reason；审计查询/脱敏；日志扫描。

## 10. 正常流程

用户选择范围→强确认→REQUESTED/VALIDATING→立即 HIDDEN→内部/对象/Provider 删除→备份窗口记录→COMPLETED；管理员敏感访问先 MFA+reason→最小查看→审计。

## 11. 空态、错误、拒绝、取消和恢复

无数据导出为空包说明；非法 scope 拒绝；legal hold 显示 BLOCKED；删除开始前可按规则取消，HIDDEN 后不可虚假恢复；外部失败 PARTIAL_FAILED 可有界重试；管理员无 reason/role 拒绝。

## 12. 后端模块

governance domain `RetentionPolicy/DataExportRequest/DeletionRequest/AuditEvent/AdminAccessPolicy`；application saga/admin authorization；adapters per-schema deleter/storage/provider/audit；worker。

## 13. 前端页面和组件

`/app/settings/privacy`、`/app/settings/privacy/requests/:id`、`/admin/audit`、`/admin/privacy-requests`；`DataInventory`、`RetentionNotice`、`DeleteScopeDialog`、`RequestStatusTimeline`、`AdminReasonDialog`。

## 14. 数据实体、约束和迁移

`governance.retention_policy/consent_record/export_request/deletion_request/deletion_step/audit_event/admin_access_request`。append-only 审计；request version/tenant；step 不存被删正文；各 schema 提供 owner deletion port。

## 15. REST/SSE/WebSocket/API 或事件

privacy inventory/consents/export/delete/account closure/status；admin audit/access；SSE request progress 可选；事件 `governance.deletion.step.*` 仅引用。无 WS。

## 16. Agent/Prompt/Provider

Agent 不决定删除/保留/授权；Provider adapter 提供可核验 delete/status 或标 unsupported；Prompt/模型调用在 consent revoked 后被确定性拒绝。

## 17. 安全与隐私

本期核心：默认拒绝、step-up auth、最小权限、reason/ticket、audit、脱敏、Secret/正文日志扫描、导出短链/过期/下载审计、删除不可逆确认。

## 18. 计划新增文件树

```text
interview-domain/.../governance/{RetentionPolicy,DataExportRequest,DeletionRequest,AuditEvent,AdminAccessPolicy}.java
interview-application/.../governance/{RequestExport,RequestDeletion,RunDeletionSaga,AuthorizeAdminAccess}.java
interview-adapters/.../{persistence/governance,provider/deletion,inbound/rest/privacy,inbound/rest/adminaudit}/
interview-boot/.../db/migration/V###__create_governance_deletion_audit.sql
frontend/src/features/privacy/; frontend/src/features/admin/{audit,privacy}/
contracts/openapi/{privacy,audit}.yaml
docs/security/{data-classification,threat-model,log-redaction}.md
```

## 19. 计划修改文件树

```text
各逻辑域 application 中的 DeletionPort 实现/公开边界
interview-application/.../voice/DeleteAudioArtifact.java
interview-boot/.../SecurityConfiguration.java
frontend/src/app/router.tsx
docs/reference/data-ownership.md
```

## 20. 后续 TASK 拆分建议

数据盘点/策略、导出、删除 saga/各 owner、Provider/对象/备份、admin security/audit、UI、威胁/日志、隐私故障/UAT；MFA/外部删除另授权。

## 21. 验证建议

单元：state/policy；集成：各 schema/部分失败；契约：Provider deletion；UI：确认/status；security：越权/MFA/log；privacy UAT；故障注入：对象/Provider失败。不能由测试证明法律合规。

## 22. 明确完成标准

所有数据/外部系统有 retention/deletion owner；用户看到真实状态；部分失败不冒充完成；内容管理员无敏感访问；审计可查且无正文；撤回阻断新处理。

## 23. 本期不能证明什么

不能证明法律合规、所有司法辖区、备份瞬时清除、外部供应商诚信或 B2B 隔离。

## 24. 风险与停止条件

数据 owner 缺失、删除状态不可回读、Provider 无法满足却仍上线、审计保存正文、共享管理员、部分失败可手改完成时停止。

## 25. 下一期进入条件

真实订单/支付和生产运维不会引入无 owner 高敏数据；隐私/管理员门有专项审查输入。

## 26. 建议学习和复盘内容

复盘 privacy by design、data lifecycle、saga/compensation、backup deletion semantics、step-up authentication、audit immutability 和威胁建模。
