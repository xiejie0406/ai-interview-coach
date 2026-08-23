# Wave 4 入站与前端落地多窗口提示词索引

> 文档类型：多窗口提示词索引 / Draft  
> 文档状态：Draft  
> 风险等级：L3  
> 当前主阶段：阶段 3 功能规格 / WaitingForApproval  
> 运行证据：NotRun  
> 更新时间：2026-08-03  

## 1. 为什么拆分

原窗口 24 同时承担 64 个 OpenAPI operation、SSE、WebSocket 与 Security，原窗口 25 同时承担
全部前端 feature；它们适合作为范围说明，不适合多个 Agent 直接并行写入。本索引把它们拆为互斥目录，
但不新增 Feature/Phase，也不成为 `tasks.md` 的第二事实源。

## 2. 推荐运行顺序

1. 只读协调：[窗口 33](window-33-wave4-coordinator.md)。
2. 基础边界可并行：
   - [窗口 34：Inbound Common / Security Foundation](window-34-inbound-common-security-foundation.md)
   - [窗口 41：Frontend Shared Foundation](window-41-frontend-shared-foundation.md)
3. 窗口 34 交接后，后端按每批最多 3 个运行：
   - 第一批：[35](window-35-rest-identity-catalog-practice.md)、[37](window-37-rest-evaluation-learning.md)、[39](window-39-sse-durable-replay.md)
   - 第二批：[36](window-36-rest-interview-voice.md)、[38](window-38-rest-billing-governance-operations.md)、[40](window-40-websocket-voice.md)
4. 窗口 41 交接后，三个前端 feature 窗口可并行：
   - [42：Identity / Catalog / Practice / Status](window-42-frontend-identity-catalog-practice.md)
   - [43：Interview / Voice](window-43-frontend-interview-voice.md)
   - [44：Evaluation / Learning / Billing / Privacy / Admin](window-44-frontend-evaluation-learning-governance.md)
5. 后端 35–40 全部交接后：[窗口 45：Inbound Reconciliation](window-45-inbound-reconciliation.md)。
6. 前端 42–44 及窗口 45 交接后：[窗口 46：Frontend Reconciliation](window-46-frontend-reconciliation.md)。
7. 窗口 45/46 后：[窗口 47：Boot / Security / Wiring](window-47-boot-security-wiring.md)。
8. 所有写窗口结束后：[窗口 48：Integration Static Review](window-48-integration-static-review.md)。

## 3. 并行图

```text
33 coordinator
├─ 34 inbound foundation ─┬─ 35 REST identity/catalog/practice ─┐
│                         ├─ 37 REST evaluation/learning ───────┤
│                         ├─ 39 SSE durable replay ─────────────┤
│                         ├─ 36 REST interview/voice ───────────┤→ 45 inbound reconcile ─┐
│                         ├─ 38 REST billing/governance/ops ────┤                       │
│                         └─ 40 WebSocket voice ────────────────┘                       ├→ 47 boot → 48 review
└─ 41 frontend foundation ┬─ 42 identity/catalog/practice ─────┐                       │
                          ├─ 43 interview/voice ────────────────┤→ 46 frontend reconcile┘
                          └─ 44 eval/learning/billing/privacy ──┘
```

上图表示依赖，不表示允许同时打开全部写窗口。任何时刻最多 3 个写窗口；同一目录只能有一个 owner。

## 4. 统一强规则

- 项目固定为 `D:\2025Ai\26-05-23\ai-interview-coach`，与 PaiCLI 完全独立。
- 当前仍是 L3、阶段 3 `WaitingForApproval`、Draft 候选、运行证据 `NotRun`。
- 不修改截断的 `docs/product/prd.md`、`docs/architecture/technical-architecture.md` 或既有
  `docs/phases/phase-*.md`。
- 不新增测试代码、不安装依赖、不构建/测试/启动、不执行 Migration/数据库、不调用真实
  Provider/ASR/TTS/支付/对象存储、不部署、不执行任何 Git 动作。
- 公共契约冻结；发现裂缝只登记，不由单个实现窗口顺手修改 contracts/application/domain/persistence。
- 上游未交接、目录已占用、需要越界、Migration 冲突、发现 Secret/PaiCLI/敏感日志时立即停止并交给 33。

## 5. 与旧窗口的关系

- 选择本拆分方案后，不再并行运行旧窗口 24/25/29/26；对应职责由 34–48 取代。
- 旧窗口 24/25 仍保留作范围对照，不删除、不写成 Superseded，因为它们仍是 Draft 提示词。
- 正式行为仍只能由后续 Approved Feature `tasks.md` 与执行包授权；本索引不批准产品行为。
