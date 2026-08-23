# AI Interview Coach 代码落地就绪审查

> 文档类型：代码落地就绪审查 / Draft  
> 文档状态：Draft  
> 风险等级：L3  
> 当前主阶段：阶段 3 功能规格，WaitingForApproval  
> 证据结果：NotRun  
> 更新时间：2026-08-03  
> 适用范围：AI Interview Coach 本地源码落地与多窗口协作  

## 1. 结论

当前文档已经足够支撑“候选实现继续落地”，但还不足以支撑“功能规格 Approved、测试验收、上线准备或真实外部集成”。代码可以继续按垂直边界推进，但每个窗口必须保持候选实现口径，并把未获批准的产品行为、供应商版本、真实支付/语音/模型调用、数据删除 SLA 和生产部署能力留作显式决策任务。

## 2. 当前文档完整度判断

| 主题 | 当前判断 | 继续落地方式 |
|---|---|---|
| 产品范围 / REQ / AC | PRD 截断点之后不能猜写，主阶段仍 WaitingForApproval | 只实现已在任务候选、路线和契约包中反复出现的稳定能力；不新增产品承诺 |
| 技术架构 | 多 module、契约、状态机、Provider、Voice、Billing、Governance 已具备候选方向 | 允许 framework-free domain/application、adapter stub、迁移候选继续细化 |
| 十八期路线 | 已合并为单份 Draft 路线，可指导切片顺序 | Phase 只做编排，不替代 Feature tasks.md |
| Feature tasks | 已有候选任务包，但未批准 | 多窗口按“允许范围 + 禁止范围 + 停止条件”执行，完成后回填差异 |
| 公共契约 | Report 映射、Learning 恢复、金额指数、隐私挑战/导出、Admin 投影和 Voice ACK/flow-control 已形成更具体候选；Adapter/DTO 尚未全部对齐 | 以当前 OpenAPI/AsyncAPI/JSON Schema 做实现输入；不把候选契约写成 endpoint 已存在 |
| 语音闭环 | 已明确 Artifact/ASR/TTS/删除生命周期及 ticket/generation/sequence/ACK/NACK/背压/取消协议 | 只实现禁用默认、ticket/preflight/metadata 与协议状态；真实上传、Provider 和运行证据继续禁用 |
| 计费权益 | 已明确 quota/reservation/ledger/cost ledger 方向 | 先做内部账本与幂等，不接真实支付 |
| 测试与验收 | Golden Set、UAT、故障注入已规划 | 未授权前不新增测试代码、不运行构建测试 |

## 3. 代码落地推荐顺序

1. 先补齐 domain/application 的核心状态机、用例、端口和脱敏边界，让业务规则有稳定 owner。
2. 再补齐 persistence/migration 候选，确保 tenant、owner、version、outbox、job、idempotency、reservation、artifact lifecycle 有数据库落点。
3. 然后实现 REST/SSE/WebSocket inbound adapter，把公共契约映射到 application use case，但保持缺失契约显式失败。
4. 同步修正前端 contract alignment：只调用已存在 API，缺口页面使用真实错误/空态，不保存敏感 token 或音频 Blob。
5. 最后做只读集成审查，列出编译前 blockers；只有用户单独授权后才写测试、运行构建或启动服务。

## 4. 不应现在做的事

- 不修补被截断的 PRD 和技术架构原文。
- 不把 Draft phase、tasks 或提示词写成 Approved。
- 不运行 Maven/npm 构建、测试、服务或迁移。
- 不调用真实 LLM、ASR、TTS、支付、对象存储、邮件或短信。
- 不创建 Git 分支、add、commit、push 或 PR。
- 不引入 PaiCLI 源码、Jar、module、runtime、配置、数据库、前端或 Git 历史。

## 5. Wave 3 并行窗口建议

Wave 3 当前使用 1 个只读协调窗口和 12 个执行/审查窗口：3 个首批并行窗口、1 个 Core 边界收口窗口、2 个必须串行的业务契约收口窗口、1 个持久化契约回填窗口、1 个 durable SSE replay 前置窗口、2 个第二批并行实现窗口、1 个 Composition Root 收口窗口和 1 个只读集成审查窗口：

| 窗口 | 文件 | 是否写代码 | 主要目标 |
|---|---|---:|---|
| 20 | window-20-wave3-coordinator.md | 否 | 登记目录 owner、启动门和交接，阻止并发覆盖与状态误报 |
| 21 | window-21-evaluation-report-learning.md | 是 | Evidence、Rubric Judge、Report、Learning Coach |
| 22 | window-22-persistence-migrations.md | 是 | JDBC/SQL 候选、tenant 组合约束、outbox/job/idempotency |
| 23 | window-23-core-application-services.md | 是 | identity/catalog/practice/interview/billing/governance 用例实现 |
| 31 | window-31-core-application-boundary-reconciliation.md | 是 | 修复跨域 internal import、幂等四态、精确回放与匿名注册 scope |
| 27 | window-27-evaluation-learning-contract-reconciliation.md | 是 | 把窗口 21 骨架对齐四个 JSON Schema、REST 状态和 durable Job receipt |
| 28 | window-28-interview-snapshot-voice-commit.md | 是 | 对齐权威 InterviewSnapshot，并让 Transcript 确认、AnswerVersion 与 next Job 原子提交 |
| 30 | window-30-persistence-contract-reconciliation.md | 是 | 把窗口 22 的 Repository/SQL 回填到 27/28 收口后的权威模型 |
| 32 | window-32-durable-sse-replay.md | 是 | durable stream_event、原子 sequence、Last-Event-ID replay 与 cursor expiry |
| 24 | window-24-rest-sse-websocket-adapters.md | 是 | inbound REST/SSE/WebSocket 映射与安全失败 |
| 25 | window-25-frontend-contract-alignment.md | 是 | 前端 API facade、页面恢复、错误/空态和敏感缓存 |
| 29 | window-29-boot-session-security-wiring.md | 是 | Session/tenant/CSRF/admin 过滤链与 use-case Bean 条件装配 |
| 26 | window-26-integration-static-review.md | 否 | 只读检查冲突、契约裂缝和下一轮 blockers |

建议一次最多同时开 3 个写代码窗口。窗口 21、22、23 先并行；23 交接后先运行 31；窗口 21 的首次交接必须在 31 后经 27 修正；窗口 28 必须等待 23、27、31 且不能与 23/31 并发；窗口 30 必须等待 22、27、28、31，修复 Core 与业务模型进入持久化映射的裂缝；窗口 32 必须等待 30，关闭 durable cursor/replay 缺口；24 依赖 22、23、27、28、30、31、32 的交接；25 只以冻结 contracts/上游交接为输入，可与 24 并行；29 必须等待 24 且不能与 24 并发；26 必须最后做。

## 6. 进入真实测试前的最小缺口

- PRD 截断与技术架构数据模型截断仍需人工修订或明确保留。
- Feature tasks.md / 执行包仍需用户批准后才能成为开发事实源。
- 上述公共 API 裂缝已经有具体候选 schema，但 Java application DTO、Persistence、REST/SSE/WS、Boot 装配和前端仍需逐项实现并做静态对齐；文件存在不代表可调用。
- Maven/npm 构建、测试代码、测试执行、浏览器 UAT 和 Golden Set 运行均需单独授权。

## 7. Durable Stream 之后的入站/前端拆分

窗口 32 已形成 PostgreSQL durable stream 候选后，旧窗口 24/25 的范围仍然正确，但单窗口过大。
后续推荐改用 [`code-landing-wave4-index.md`](../phases/window-prompts/code-landing-wave4-index.md)：

- 34/41 先分别冻结 inbound common/security 与 frontend shared/session。
- REST 按 35–38 的互斥业务目录拆分；SSE/WS 分别由 39/40 独占。
- Frontend feature 按 42–44 拆分，不在并行期改 shared/router。
- 45/46 串行收口后由 47 做 Composition Root；48 最后只读审查。

采用 Wave 4 时暂停旧窗口 24/25/29/26，避免相同目录出现第二 owner。任何时刻仍最多 3 个写窗口。
