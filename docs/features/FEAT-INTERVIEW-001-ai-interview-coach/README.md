# AI Interview Coach 完整产品 Feature 控制页

> 文档类型：Feature 控制页  
> 文档状态：Draft  
> owner / 责任边界：产品 owner 维护产品规格；架构/任务/质量 owner 维护对应下游；用户形成批准和验收结论  
> 创建时间：2026-08-02  
> 更新时间：2026-08-29  
> Feature ID：FEAT-INTERVIEW-001  
> 风险等级：L3  
> 当前阶段：3 功能规格  
> 阶段状态：WaitingForApproval  
> 目标版本：RuoYi V2（题库 → 文本面试 → Web 语音）

> **V2 收敛说明**：本控制页原先描述的 18 期完整产品路线已不再是本轮实施范围。当前唯一执行基线是 [`../FEAT-QBANK-001-public-catalog/`](../FEAT-QBANK-001-public-catalog/) 与 [`../../architecture/ruoyi-qbank-voice-convergence-plan.md`](../../architecture/ruoyi-qbank-voice-convergence-plan.md)；支付、学习计划、报告扩展、移动 App 和多 Provider 均延期，不得以本页旧任务状态宣称已实现。

## 1. 目标与非目标

- 目标：在若依统一 JWT/RBAC 下完成面试题库、最小文本面试会话和 Web 语音面试闭环；题库与语音是本轮 P0。
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

- 阻断：V2 Feature Spec/技术设计仍为 Draft；构建、测试、启动、Provider、浏览器 UAT 和删除尚未获得独立授权。
- 当前已获授权：按 V2 执行包进行本地源码和文档修改；用户目标明确要求开始实现。
- 仍需独立决定：测试代码、构建/测试/启动、真实 Provider/语音/支付、部署、Git。
- 下一门：题库与语音代码只读审查完成，随后分别请求验证授权；验收完成后再请求旧项目精确删除清单确认。
- 退回条件：产品范围/权限/数据/AC 变化回阶段 3；公共契约不成立回阶段 5。

### 4.1 工作流状态

| 工作流 | owner | 当前阶段 | 状态 | 上游 | 下一门 |
|---|---|---|---|---|---|
| PRODUCT-RECOVERY | 主会话 | 3/5 | Superseded | 旧 PRD/18 期路线 | 以 RuoYi V2 收敛方案为准 |
| PROTOTYPE-PACK | 主会话 | 4 | Skipped | 本轮 Web 优先，复用现有页面 | 关键交互由代码与 UAT 验证 |
| ARCH-CONTRACTS | 主会话 | 5 | InProgress | RuoYi V2 收敛方案 | 题库/语音契约审查 |
| MASTER-TASKS | 主会话 | 6 | InProgress | V2 Feature 任务包 | 验证执行包授权 |
| IMPLEMENTATION | 主会话 + 子 Agent | 7 | InProgress | 题库/语音分片 | 阻断问题关闭后进入阶段 8 |

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
