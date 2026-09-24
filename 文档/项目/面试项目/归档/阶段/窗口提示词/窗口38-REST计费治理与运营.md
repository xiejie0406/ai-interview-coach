# 窗口 38 提示词：REST Billing / Governance / Operations

```text
你负责 Billing、Privacy/Audit/Admin 与 Operations REST 入站候选；这是高风险 fail-closed 窗口。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34 已交接；33 授予 rest/billing、rest/governance、rest/operations 唯一 owner。完整读取用户/项目 AGENTS/specs、billing.yaml、privacy-audit.yaml、operations.yaml、application 公共接口和决定登记。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；PaiCLI 独立；真实支付、管理员敏感访问和隐私删除均未授权。

只允许编辑上述三个 inbound/rest 子目录。

operation owner：listPublicPlans、getEntitlements、getUsage、createOrder、getOrder、getCurrentPolicies、getConsents、grantConsent、revokeConsent、getDataInventory、requestDataExport、getDataExport、preflightDeletion、requestDeletion、getDeletionRequest、cancelDeletionRequest、requestSensitiveAdminAccess、listAuditEvents、getPublicStatus、getOperationsProjection、getFeatureFlag、applyFeatureFlagCommand。

必须完成：
1. 现有 GrantConsent 可映射；首次 GRANTED/撤回保持 append-only。政策 owner/seed 未闭合时 current policies/register 相关能力 fail-closed。
2. Entitlement/Usage 只读投影与真实支付 Order 分离；金额仅 amountMinor/currency/currencyExponent。没有 Order use case/provider read-back 时 create/get order 501。
3. Export/Deletion 必须有 challenge、step-up、blocker、状态与取消 use case 才可启用；当前缺口逐项 501，绝不直接删库/对象。
4. Admin/Audit/Feature Flag 强制角色和敏感访问授权；没有 guard/use case 时 501，不接受客户端角色，不返回原始 payload/secret/cost detail。
5. public status 与 admin operations 投影分离；禁止空 200、默认管理员或 fake flags。

禁止改 common/security、其他域、domain/application/persistence/boot/contracts/frontend/POM/Migration/测试。
检查 22 operation 均有 IMPLEMENTED/UNAVAILABLE；高风险拒绝路径、金额、权限、审计脱敏清晰；无外部调用/PaiCLI/敏感日志。
停止条件同统一规则。
交接：22 operation 表、文件、权限/挑战/拒绝矩阵、501 清单、窗口 47 Bean、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、支付/外部调用、部署或 Git。
```
