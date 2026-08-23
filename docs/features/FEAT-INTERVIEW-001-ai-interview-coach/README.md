# AI Interview Coach 完整产品 Feature 控制页

> 文档类型：Feature 控制页  
> 文档状态：Draft  
> owner / 责任边界：产品 owner 维护产品规格；架构/任务/质量 owner 维护对应下游；用户形成批准和验收结论  
> 创建时间：2026-08-02  
> 更新时间：2026-08-03  
> Feature ID：FEAT-INTERVIEW-001  
> 风险等级：L3  
> 当前阶段：3 功能规格  
> 阶段状态：WaitingForApproval  
> 目标版本：T0 → T1 → T2（规划/未实现）

## 1. 目标与非目标

- 目标：完成面向 Java 开发者转型 AI 应用/Agent 开发的题库、文本/级联语音模拟面试、证据化评测、复练、个人权益、隐私和生产治理闭环。
- 非目标：Realtime/WebRTC、B2B、SSO/SCIM、Kubernetes、微服务、Kafka、独立向量库、视频数字人、声音克隆、真实面试隐蔽代答。

## 2. 产物与版本

| 产物 | 唯一路径 | 文档状态 | 阶段/状态 | 备注 |
|---|---|---|---|---|
| 调研 | [`../../product/research.md`](../../product/research.md) | Draft | 2 / Completed | 仍有用户证据缺口 |
| Canonical Spec | [`../../product/prd.md`](../../product/prd.md) | Draft | 3 / WaitingForApproval | `FUNC-VOICE-002` 后截断 |
| 统一产品审查与恢复候选 | [`../../review/canonical-product-recovery-draft.md`](../../review/canonical-product-recovery-draft.md) | Draft（已合并） | 3 / WaitingForApproval | 唯一产品审查入口；不替代 Canonical |
| 原型实现 | [`../../../prototype/html-v1/README.md`](../../../prototype/html-v1/README.md) | Draft | 4 / NotStarted | 前瞻 Mock 原型，非生产实现 |
| 原型资料包 | `../../prototypes/FEAT-INTERVIEW-001/` | Draft | 4 / NotStarted | 待阶段 3 批准后评审 |
| 技术战略 | [`../../architecture/technical-architecture.md`](../../architecture/technical-architecture.md) | Draft | 5 / NotStarted | 前瞻输入；数据模型段截断 |
| 架构深化 | [`../../phases/architecture-review.md`](../../phases/architecture-review.md) | Draft | 5 / NotStarted | 前瞻推荐边界 |
| 技术契约候选 | `../../architecture/implementation-contract-pack.md` | Draft | 5 / NotStarted | 前瞻候选；不替代技术战略 |
| 任务与执行包 | [`tasks.md`](tasks.md) | Draft | 6 / NotStarted | 候选拆分，未批准为执行事实源 |
| 验证 | `verification.md` | 未创建 | 8 / NotStarted | 实施并获验证授权后创建 |
| 验收 | `acceptance.md` | 未创建 | 9 / NotStarted | 用户形成结论 |
| 上线就绪 | `release-readiness.md` | 未创建 | 10 / NotStarted | NotAssessed / NotReleased |

## 3. 追溯入口

- FIND/REQ/BR/AC：Canonical PRD；截断恢复候选仅供评审。
- DES：技术架构、架构深化、技术契约候选。
- TASK：本目录 `tasks.md`，是批准后唯一实施任务源。
- EV/UAT：尚未形成；原型 Mock 检查不能替代生产 EV/UAT。

## 4. 当前控制信息

- 阻断：Canonical 截断、Gate A/C 未关闭、原型未获用户评审、设计未 Approved、执行包未 Approved。
- 当前已获授权：文档完善与子 Agent 并行；用户目标明确要求最终完成编码。
- 仍需独立决定：测试代码、构建/测试/启动、真实 Provider/语音/支付、部署、Git。
- 下一门：产品/原型/技术候选合并并取得用户决定，随后批准 `tasks.md` 执行包。
- 退回条件：产品范围/权限/数据/AC 变化回阶段 3；公共契约不成立回阶段 5。

### 4.1 工作流状态

| 工作流 | owner | 当前阶段 | 状态 | 上游 | 下一门 |
|---|---|---|---|---|---|
| PRODUCT-RECOVERY | product_recovery Agent | 3 | WaitingForApproval | PRD/统一产品审查与恢复候选 | 用户决定、回写 Canonical |
| PROTOTYPE-PACK | prototype_pack Agent | 4 | NotStarted | 前瞻 `prototype/html-v1` Draft | 阶段 3 通过后用户原型评审 |
| ARCH-CONTRACTS | architecture_contracts Agent | 5 | NotStarted | 前瞻技术战略/架构深化 Draft | 阶段 4 通过后架构评审 |
| MASTER-TASKS | 主 Agent | 6 准备 | NotStarted | 18 期 Draft 路线 | 上游通过后执行包批准 |
| IMPLEMENTATION | 待分配 | 7 | NotStarted | Approved tasks | 分波次编码 |

### 4.2 独立授权矩阵

| 动作 | 范围 | 授权状态 | 依据 | 限制 |
|---|---|---|---|---|
| 文档修改 | 本项目规划/Feature 资料 | Approved | 用户要求完善并全程推进 | 不伪造批准/证据 |
| 生产代码 | 本项目内按 `tasks.md` 候选和互斥窗口推进的本地源码/配置 | Approved | 用户明确要求多个子 Agent 持续推进，最终目标为编码全部完成 | 不把候选行为发布为承诺；公共契约变化须单一 owner；外部动作不包含 |
| 测试代码 | 待 tasks 列明 | NotRequested | 无 | 不创建 |
| 构建/测试/启动 | 待精确命令 | NotRequested | 无 | 不执行 |
| 真实外部系统/费用 | Provider/语音/支付/云 | NotRequested | 无 | 不调用 |
| Git 各动作 | add/commit/push/PR/tag | NotRequested | 无 | 不执行 |

## 5. 上线与发布事实

- 上线就绪结论：NotAssessed
- 发布事实：NotReleased

## 6. 变更记录

| 日期 | 变化 | 原因 | 状态影响 |
|---|---|---|---|
| 2026-08-02 | 建立完整产品控制页与候选主任务源 | 用户要求启用多个子 Agent 并完成全部编码 | 主阶段仍为 3；未进入实现 |
| 2026-08-02 | 启动 Foundation 局部实现工作流 | 用户继续要求以全部编码完成为目标 | 主阶段仍为 3；源码实现与运行证据分离，详见开发记录 |
