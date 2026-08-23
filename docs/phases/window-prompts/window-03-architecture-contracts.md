# 窗口 03 提示词：数据、API、状态与时序契约包

```text
你负责 AI Interview Coach 的阶段 5 技术契约恢复包。当前技术架构的数据模型段有截断；不要直接修改或伪装修复原文，只在新 Draft 中形成可评审候选。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

完整读取：项目/用户 AGENTS.md、docs/specs/README.md、docs/product/prd.md、docs/architecture/technical-architecture.md、docs/phases/architecture-review.md、dependency-matrix.md、file-structure-blueprint.md、implementation-readiness.md，以及 architecture-spec、development-spec、feature-spec、documentation-spec。

独占输出：
docs/architecture/implementation-contract-pack.md

至少包含：
1. 文档状态 Draft、假设和截断阻断说明。
2. 五 Maven module 与 11 逻辑域/一个 platform 域的依赖表。
3. 每个 PostgreSQL schema 的实体候选：主键、tenant、版本、幂等、敏感级、生命周期、写 owner、迁移。
4. REST/SSE/WebSocket/Webhook 的 endpoint/event 清单、认证、错误、重试、取消、续传和兼容。
5. InterviewSession、Job、DeletionRequest、UsageReservation、AudioArtifact、Prompt/Schema lifecycle 状态图。
6. 文本面试、级联语音、异步评测、删除、支付回调的正常/失败/取消/恢复时序。
7. 公共错误码、事件信封和 correlation/trace ID 规则。
8. 待用户/架构 owner 批准的 DES 候选和 Gate B 决策。

不得：
- 修改 technical-architecture.md、PRD、决策登记或 Phase。
- 锁定未经核验的版本/供应商。
- 创建 OpenAPI/源码/migration；本窗口只写候选设计。
- 把候选 DES 当作 Approved。

交接时给出：阻断设计项、可并行实现边界、必须串行的共享契约、建议回写位置和未执行项。
```
