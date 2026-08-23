# AI Interview Coach 项目规范索引

> 文档类型：规范索引  
> 文档状态：Draft  
> owner / 责任边界：项目 owner 维护项目事实映射；通用规则由用户级规范维护  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> 适用范围：AI Interview Coach 全生命周期  
> 关联：[`../../AGENTS.md`](../../AGENTS.md)、[`../phases/README.md`](../phases/README.md)

## 1. 优先级与边界

1. 当前会话中更高优先级的明确指令。
2. `C:\Users\admin\.codex\AGENTS.md` 和 `C:\Users\admin\.codex\specs\` 中的 Approved 通用规范。
3. 本项目 `AGENTS.md`、本索引和后续项目级 Approved 规范。
4. 已批准的具体 Feature Spec、原型、设计与契约。
5. 当前代码、配置和实际证据只说明现状，不自动批准产品行为。

本项目与 PaiCLI 完全独立；PaiCLI 只可作为设计参考，不是依赖、运行时或事实源。

## 2. 用户级规范路由

| 任务/阶段 | 用户级事实源 |
|---|---|
| 十阶段、风险、阶段门、任务拆分 | `C:\Users\admin\.codex\specs\agent-governance.md` |
| 调研、PRD、旅程、原型 | `product-spec.md`、`feature-spec.md` |
| 模块、数据、API、权限、失败恢复 | `architecture-spec.md` |
| 源码、配置、依赖、Agent 与集成 | `development-spec.md` |
| 审查、测试、验收、上线、Git | `quality-delivery-spec.md` |
| 文档、状态、Feature 资料包与历史保护 | `documentation-spec.md` |

相对文件名均位于 `C:\Users\admin\.codex\specs\`。

## 3. 项目事实与目录映射

| 事实 | 唯一入口 | 当前状态 |
|---|---|---|
| 项目边界和强规则 | [`../../AGENTS.md`](../../AGENTS.md) | Draft 项目入口 |
| 市场与方案调研 | [`../product/research.md`](../product/research.md) | Draft |
| 产品范围、REQ/BR/AC | [`../product/prd.md`](../product/prd.md) | Draft / WaitingForApproval |
| 技术架构、DES 与模块边界 | [`../architecture/technical-architecture.md`](../architecture/technical-architecture.md) | Draft / InProgress |
| RuoYi 身份与后端单体收敛设计 | [`../architecture/ruoyi-platform-convergence.md`](../architecture/ruoyi-platform-convergence.md) | Approved / 阶段 7 实施中 |
| 决策状态 | [`../decisions/decision-register.md`](../decisions/decision-register.md) | Draft；仅 DEC-001 Accepted |
| 跨功能实践路线 | [`../phases/README.md`](../phases/README.md) | Draft；不替代 Feature tasks.md |
| RuoYi 身份与后端收敛 Phase / TASK 计划 | [`../phases/phase-19-ruoyi-platform-convergence.md`](../phases/phase-19-ruoyi-platform-convergence.md) | Approved / InProgress |
| 完整产品 Feature 控制与任务候选 | [`../features/FEAT-INTERVIEW-001-ai-interview-coach/README.md`](../features/FEAT-INTERVIEW-001-ai-interview-coach/README.md) | Draft / InProgress；tasks 未批准 |
| 静态交互原型 | [`../../prototype/html-v1/README.md`](../../prototype/html-v1/README.md) | Draft / Mock；用户验收 NotRun |
| 当前实现记录 | [`../development-records/2026-08-02-foundation-wave-01.md`](../development-records/2026-08-02-foundation-wave-01.md) | 局部实现中；运行证据 NotRun |
| 多端前端方案 B（2026-08-16 用户对话生效） | [`../decisions/decision-register.md`](../decisions/decision-register.md) 第 2.4 节 / [`../licenses/ruoyi-vue3.md`](../licenses/ruoyi-vue3.md) / [`../architecture/ruoyi-migration-plan.md`](../architecture/ruoyi-migration-plan.md) | DEC-070/071/072/073 已填 A；4 份 SPEC 已重写为方案 B（仍 Draft）；上游评审报告与迁移计划 Draft / 用户未签收；物理动作（含 `git clone`）必须等用户单独授权 |
| 4 份 SPEC（方案 B 重写） | [`./SPEC-admin-web-ruoyivue3.md`](./SPEC-admin-web-ruoyivue3.md) / [`./SPEC-portal-web.md`](./SPEC-portal-web.md) / [`./SPEC-mobile-uniapp.md`](./SPEC-mobile-uniapp.md) / [`./SPEC-backend-bridges.md`](./SPEC-backend-bridges.md) | 全部 Draft；状态变更 Draft → Approved 需用户单独批准 |
| 环境准备说明 | [`../setup/environment-setup.md`](../setup/environment-setup.md) | 规划/未执行 |

## 4. 后续标准目录

```text
docs/
├── specs/                  项目规范索引与项目特有规范
├── features/               每个 L2/L3 Feature 的端到端资料包
├── phases/                 跨 Feature 实践期、里程碑与依赖编排
├── development-records/    实际动作、差异与交接时间线
├── reference/              当前实现参考
├── research/               跨功能专题调研
└── releases/               发布批次与实际发布/回滚记录
```

目录只在产生真实内容时创建，不机械建立空壳。单 Feature 的可执行任务只能写入其 `docs/features/<FEAT-ID>-<slug>/tasks.md`；Phase 文档只说明目标、交付切片、预期文件范围和阶段门。

## 5. 当前门禁

- 当前主阶段仍为 3 功能规格，状态 `WaitingForApproval`。
- PRD 在 `FUNC-VOICE-002` 后、技术架构的数据模型段分别存在一处字面截断标记；正式批准前必须修订和复核，不能按丢失正文猜测任务。
- Gate A 决策尚未逐项变为 `Accepted`；当前路线只能按推荐包作为规划假设。
- 静态 HTML Mock 原型已经存在并记录原型范围检查，但原型资料包/用户评审、Approved PRD、Approved 技术设计和 Approved Feature `tasks.md`/执行包仍未完成。
- 用户已授权在本项目任务候选和互斥窗口内持续修改本地生产源码/配置；局部代码不能覆盖上述产品/设计门禁，也不代表当前主阶段已进入 7。
- 测试代码、依赖安装、构建、测试、启动、外部 Provider/语音/支付、部署和 Git 仍未独立授权；证据结果保持 `NotRun`。
- **2026-08-16 方案 B 生效后**：DEC-070/071/072/073 + DEC-022 用户决定列已填 A；4 份 SPEC 已重写为方案 B 口径，仍 Draft；`docs/licenses/ruoyi-vue3.md`（上游评审报告）与 `docs/architecture/ruoyi-migration-plan.md`（迁移计划）已新建为 Draft。**`git clone RuoYi-Vue3` / 创建 `apps/admin-web` / 移动或删除 `frontend/` / 修改 `backend/pom.xml` 等物理动作仍按决策表第 167 行 + 第 2.4 节条件，全部需用户单独授权**。
