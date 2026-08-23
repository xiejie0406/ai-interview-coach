# 2026-08-03 Core Application 边界收口记录

> 文档状态：Draft  
> 风险等级：L3  
> 当前主阶段：阶段 3 / WaitingForApproval  
> 证据结果：NotRun  

## 1. 输入

窗口 23 已交接 Identity、Consent、Catalog、Practice、Billing、Interview 与 Platform application/internal 候选实现。其只读检查没有发现 package/import/框架反向依赖，但交叉审查发现以下语义问题：

- Practice、Interview、Billing、Governance 直接 import `identity.internal.ActivePrincipalGuard`。
- 多数 IdempotencyGuard 调用点只处理 `IN_PROGRESS/REPLAY_SUCCESS`，遗漏 `REPLAY_FAILURE`，可能让已记录失败继续执行副作用。
- Interview 的异步回放用当前请求时间重建 `OperationAccepted`，不是第一次受理的精确回执。
- 匿名注册使用虚构 `public` tenant 作为幂等 scope，而 migration 要求 tenant 外键且没有该 tenant。
- `InterviewSessionSnapshot`、Voice Confirm 原子回答、Evaluation/Learning、REST/Security/Boot 仍需后续专门窗口。

## 2. 本次修改

- 新增公共 `application.identity.ActivePrincipalGuard`，Identity 内部实现该接口；其他逻辑域不再依赖 Identity internal 包。
- 新增公共 `application.platform.ServerSideDigest`；旧 internal 类仅作为 deprecated 委托保留，不再被生产候选引用。
- `DefaultIdempotencyGuard` 统一拦截 `IN_PROGRESS` 与 `REPLAY_FAILURE`；仅 `NEW/REPLAY_SUCCESS` 返回业务用例，避免调用方遗漏分支产生重复 effect。
- 新增 `IDEMPOTENCY_REPLAY_FAILURE` 应用错误类别，待 REST Adapter 形成稳定 ErrorEnvelope 映射。
- Interview 的 OperationAccepted 幂等引用保存 operationId/jobId/resourceId/statusPath/streamPath/acceptedAt；重放使用原值。
- 删除匿名命令对 `TenantId.of("public")` 的隐式依赖；pre-tenant 幂等 owner 未实现前返回 `PRE_TENANT_IDEMPOTENCY_UNAVAILABLE`，不伪造注册成功。
- `DefaultCreateInterviewSession` 对 tenant 内非 owner 的 Plan 返回不泄漏存在性的 NotFound，再单独校验 Plan 状态/版本。

## 3. 只读静态检查

- Java package/path：无新增不匹配。
- 项目内部 import：无缺失。
- 跨逻辑域 `application.<domain>.internal` import：0。
- 业务源码 PaiCLI 引用：0。
- 虚构 `TenantId.of("public")`：0。
- Interview acceptedAt/path/ID 已进入幂等引用与回放映射。

这些检查不证明 Java 可编译、事务可运行、数据库约束成立或 API 可调用。

## 4. 明确未完成

- Pre-tenant/global Idempotency 的权威 domain/persistence 模型和 migration 尚未决定；注册当前 fail-closed。
- 当前 identity.yaml 的 logout/getCurrentAccount/updateProfile 尚无完整 use case/adapter；账号找回渠道仍 Pending，不得猜写。
- RunInterviewStep 在 Agent route/budget/ActionGuard 缺失时继续 fail-closed。
- Privacy deletion、Admin permission、Audit append 尚未形成完整 application 闭环。
- InterviewSnapshot 与 Voice Confirm 原子回答由窗口 28 承接；Persistence 由窗口 30 回填；REST/Security/Boot 由窗口 24/29 承接。

## 5. 未执行

未运行 Maven/npm 构建、编译、测试、服务、migration、Provider、ASR/TTS、支付、对象存储、部署或 Git；证据保持 NotRun。

## 6. 窗口 28 后的 Billing 边界补充

Interview Snapshot / Voice 原子提交交接后的复核发现，Interview 的 Plan confirm/cancel、Session create/start 仍直接读取和保存 `BillingRepository` 聚合。当前候选已改为只依赖 Billing owner 的公共 `EntitlementPort`：

- 新增 owner + businessOperation scoped `requireActiveReservation`，Billing 内部验证 tenant、user、业务操作、`RESERVED` 与 expiry。
- `ReleaseRequest` 增加 userId 与 businessOperationId；Billing owner 自己处理 Reservation/Entitlement、幂等和 append-only 事件。
- Interview 不再 import `BillingRepository` 或 `UsageReservationState`，也不直接保存 Entitlement/Reservation。
- Snapshot 的 Reservation/Voice 状态改成 Interview consumer-owned 最小枚举，避免恢复 DTO 依赖 Billing/Voice 领域类型。
- Identity 注册不再直接写 `ConsentRepository`；新增 consumer-owned `RegistrationConsentPort`，由 Governance 在同一注册事务中按确定顺序追加 Consent facts。
- Interview Plan confirm/cancel 已对齐 OpenAPI `acknowledgedEstimateVersion`：Confirm 由服务端通过 `EntitlementPort.reserve` 创建 Reservation 后原子确认；Cancel 使用服务端固定原因释放，不接受客户端 Reservation ID 或计费原因。
- Create Interview Plan 的公开 Command 已对齐 OpenAPI `InterviewSetup`；profileVersion、题数、追问预算、expiry、selection codes 由新增 `InterviewPlanPolicyPort` 在服务端解析，REST 不再猜造内部预算或提交服务端版本字段。
- `InterviewPlanView` 现在只返回 OpenAPI 所需 questionCount、usage estimate 和 reservation reference，不再在计划确认前暴露 `PlannedQuestion` 列表或问题正文。

补充扫描后，`application.<domain>` 直接 import 其他逻辑域 `*Repository` 的数量为 0；跨域协作只保留公共 facade、最小投影或 consumer-owned 本地事务桥。

这是静态候选边界收口，尚未通过编译、事务集成或故障注入验证；证据仍为 `NotRun`。
