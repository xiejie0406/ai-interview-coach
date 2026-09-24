# 第 16 期：订单、支付、额度结算与成本账本

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-16  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：billing / operations / integration / frontend  
> 证据结果：NotRun  
> 前置：PHASE-07、14、15；经营主体/渠道/价格/退款批准；真实支付独立授权

## 1. 本期为什么存在

T2 才把已验证的 Entitlement/Reservation 映射到真实 Order/Payment，补齐结算、退款、对账与平台成本账本；避免支付先行绑架产品验证。

## 2. 用户可见目标

用户清楚比较 Free/Pro、看到预计/实际用量和订单状态，在批准沙箱/渠道完成购买或失败/取消/退款；重复回调不会重复授予权益。

## 3. 技术学习目标

学习 payment webhook、签名/防重放、乱序状态机、ledger/reconciliation、price version、refund/compensation 和单位经济。

## 4. 范围

Plan/PriceVersion、Order/PaymentAttempt/Refund、PaymentPort、checkout、webhook、Entitlement grant/revoke、Reservation settlement、UsageEvent、CostLedgerEntry、对账/客服最小视图。

## 5. 非目标

不存卡号/支付凭据、不自定价格/税务/退款政策、不做 B2B 发票/合同、不支持多渠道矩阵、不自动发布生产支付。

## 6. 前置决策

DEC-010/043/044/048/051/058；经营主体、渠道、价格/币种/版本、套餐权益、失败是否计费、退款/争议/回调验证、沙箱与生产边界。

## 7. 前置期次和依赖

07 Entitlement/Reservation；14 resilience/Flag；15 privacy/admin。支付只调用 billing application，不直接写 Identity/Interview 表。

## 8. 涉及的 REQ/BR/AC/DES

REQ-14/15；BR-09；AC-14；DES-BILLING-ORDER/PAYMENT/LEDGER、DES-API-WEBHOOK。

## 9. 本期完整功能点

套餐/价格版本；订单状态；checkout session；签名回调/幂等/乱序；权益授予/撤销；退款；用户用量/账单；Provider 成本账；差异/异常；支付 Flag；测试权益 fallback。

## 10. 正常流程

选择价格版本→创建 Order→PaymentPort→渠道完成→签名 webhook→状态机/幂等→授予 Entitlement→用户回读；业务调用预留→按实际结算→平台成本入账→对账。

## 11. 空态、错误、拒绝、取消和恢复

无订单/免费用户为空态；过期价格拒绝；签名/重放拒绝；用户取消/支付失败可重试新 attempt 不新建重复权益；乱序 webhook 缓存/回读；退款补偿；渠道故障保测试权益/稍后支付。

## 12. 后端模块

billing domain `PriceVersion/Order/PaymentAttempt/Refund/UsageEvent/CostLedgerEntry`；application checkout/webhook/settle/reconcile；adapters payment/persistence；operations read projection。

## 13. 前端页面和组件

`/pricing`、`/app/billing/checkout`、`/app/billing/orders/:id`、`/app/usage`、`/admin/billing`；`PlanComparison`、`UsageBreakdown`、`PaymentStatus`、`RetryPayment`、`CostAnomaly`。

## 14. 数据实体、约束和迁移

`billing.price_version/order/payment_attempt/refund/entitlement_grant/usage_event/cost_ledger/reconciliation_issue`。money decimal+currency；providerRef 高敏最小；webhook event unique；ledger append-only；历史价格不重算。

## 15. REST/SSE/WebSocket/API 或事件

plans/prices/orders/checkout/usage；webhook `/webhooks/payments/{provider}` 签名/时间窗/幂等；事件 `payment.*`, `entitlement.*`, `usage.settled`；SSE 可推订单状态，无 WS。

## 16. Agent/Prompt/Provider

`PaymentPort` 与 AI Provider 分离；LLM 不计算金额/额度/退款；ProviderInvocation 用量映射 CostLedger，价格表版本化且可复算。

## 17. 安全与隐私

不存卡数据/Secret；webhook 防重放/最小日志；账单/订单 tenant scope；管理员最小权限/reason；生产/沙箱 key 隔离；支付页面不嵌入未审厂商脚本。

## 18. 计划新增文件树

```text
interview-domain/.../billing/{PriceVersion,Order,PaymentAttempt,Refund,UsageEvent,CostLedgerEntry}.java
interview-application/.../billing/{CreateCheckout,HandlePaymentWebhook,RefundOrder,ReconcileUsageCost}.java
interview-application/.../agent/port/PaymentPort.java
interview-adapters/.../{provider/payment,persistence/billing,inbound/webhook/payment,inbound/rest/billing}/
interview-boot/.../db/migration/V###__create_order_payment_ledger.sql
frontend/src/features/billing/{pricing,checkout,orders,usage}/
frontend/src/features/admin/billing/
contracts/{openapi/billing.yaml,asyncapi/payment-webhooks.yaml}
```

## 19. 计划修改文件树

```text
interview-domain/.../billing/{Entitlement,UsageReservation}.java
interview-application/.../billing/{SettleUsage,ReleaseUsage}.java
interview-adapters/.../provider/{llm,asr,tts}/
frontend/src/app/router.tsx
interview-boot/.../ProviderConfiguration.java
```

## 20. 后续 TASK 拆分建议

价格/订单、PaymentPort/沙箱、webhook/security、entitlement/退款、usage/cost ledger、UI、admin/reconciliation、合同/真实支付验证；真实收款独立授权。

## 21. 验证建议

单元：order/reservation/settlement；集成：并发/ledger；payment contract：签名/乱序/重复；UI：失败/取消；故障注入：callback loss；UAT：沙箱购买/额度；Golden Set NotApplicable。沙箱不证明生产结算。

## 22. 明确完成标准

重复/乱序回调不重复授予；账本可复算；失败释放符合规则；历史价格稳定；用户看到一致用量；真实支付仅在全部授权/合规门满足时启用。

## 23. 本期不能证明什么

不能证明生产收款、税务/会计/法律合规、退款争议全覆盖、盈利或支付渠道 SLA。

## 24. 风险与停止条件

经营主体/合同未定、金额浮点、ledger 可改、LLM 计算权益、回调无签名/幂等、沙箱/生产混用或真实收款未授权时停止。

## 25. 下一期进入条件

商业状态/成本指标可供生产监控；支付可用 Flag 关闭且不影响已有用户读取/文本免费路径。

## 26. 建议学习和复盘内容

复盘 financial state machine、double-entry 思维（是否采用待设计）、webhook security、reconciliation、compensation 和 unit economics。
