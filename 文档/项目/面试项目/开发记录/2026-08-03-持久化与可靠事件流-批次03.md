# 2026-08-03 Persistence 与 Durable Stream Wave 3 记录

> 记录类型：候选生产源码与 Migration 交接记录  
> 文档状态：Draft  
> 风险等级：L3  
> 当前主阶段：阶段 3 功能规格 / WaitingForApproval  
> 运行证据：NotRun  

## 1. 本轮范围

本轮收口窗口 22/30 的 JDBC/Migration 候选，并完成窗口 32 的 PostgreSQL durable SSE
事实、cursor 和 replay 候选。它们不改变 Feature、PRD、技术设计或 Phase 的批准状态，也不表示
Migration 可执行、Spring Context 可装配或 SSE endpoint 已可用。

## 2. Persistence 交付

- Draft Migration 当前连续为 V001–V010；V002–V009 覆盖 Identity/Governance、Catalog/Practice、
  Billing、Interview、Job/Outbox/Idempotency、Voice、Evaluation/Report/Feedback 和 Learning。
- 新增/收口 Evaluation、Feedback、Learning JDBC owner：
  - `JdbcEvaluationRepository`
  - `JdbcFeedbackContentRepository`
  - `JdbcLearningPlanRepository`
- `JdbcInterviewRecoveryProjectionRepository` 以 tenant + owner + session 投影 Reservation、Report、
  Voice、多 pending Job；本轮接入真实 durable stream head cursor。
- `JdbcOutboxRepository` 不再把可变 delivery `availableAt` 当作 immutable `occurred_at` 比较。
- `JdbcVoiceRepository` 允许内容一致的同版本 Artifact 无变化保存，避免 `DELETE_QUEUED` 重试
  被误判为乐观锁冲突。
- `EncryptedEnvelope` 与 Feedback 私有正文载体使用固定脱敏 `toString()`。

## 3. Durable stream 交付

### 3.1 权威模型

- `platform.stream_head` 是 `(tenant_id, stream_type, stream_id)` 的 sequence owner；append 先锁定
  head，再通过数据库 `latest_sequence + 1 ... returning` 分配严格递增 sequence，不读取
  `max(sequence)`，也不使用 Java 进程锁。
- `platform.stream_event` 保存 event ID、stream、aggregate ID/version、sequence、公开 type、
  occurredAt、schemaVersion、correlationId、allowlisted JSON data 和 expiresAt。
- Domain event、Outbox 和 durable stream append 由 `OutboxDomainEventAdapter` 在调用方同一本地事务
  中完成；任一分类或写入失败会使事务失败。
- Outbox 继续负责外部投递；stream_event 只负责 owner-scoped 浏览器恢复，二者不能互相冒充。

### 3.2 Cursor 与 replay

| 输入状态 | 候选结果 | 下游职责 |
|---|---|---|
| cursor 与 tenant/stream/sequence/eventId 均匹配且未过期 | `VALID`，按 sequence 返回有界事件页 | SSE 逐条使用返回 cursor 作为 `id:`，随后进入 live fan-out |
| cursor sequence 低于 retention floor，或 anchor/中间事件已过期 | `EXPIRED` + stream recovery target | REST/SSE 映射为 410 `STREAM_CURSOR_EXPIRED` 与 owner snapshot URL |
| malformed、未知、跨 tenant、跨 stream 或 eventId 不匹配 | `UNKNOWN_OR_FOREIGN` | 返回不泄漏资源存在性的稳定错误，不从头静默重放 |

Cursor 使用 URL-safe Base64 的版本化 length-prefixed 结构；数据库仍重验 tenant、stream、sequence
和 eventId，不能只信 token。Retention 只删除连续过期前缀并单调推进
`retained_from_sequence`；不会回退 high-water mark，也不会复用 sequence/eventId。

### 3.3 公开事件 allowlist

- Interview Session/Turn 稳定事实映射为 `interview.session.state`；
  `interview.question.committed` 保留独立公开类型。
- Evaluation 状态迁移映射为 `evaluation.state.changed`；Report 发布映射为
  `evaluation.report.ready`。
- Interview Plan、Turn planned、Report feedback 以及其他尚无批准 SSE channel 的逻辑域显式不进入
  durable stream；Interview/Evaluation 命名空间出现未知事件时 fail closed。
- `event_data` 只保留 ID、版本、状态、计数和原因码；不保存回答、转写、Prompt、模型、支付、
  对象存储 key 或音频正文。

## 4. 同轮静态修复

- `FinishJob` 补齐 `AggregateVersion` import。
- `DefaultGrantConsent` 的首次 `GRANTED` 不再错误要求 `supersedesId`；已有 current 时才产生
  superseding fact，撤回仍必须引用已有事实。
- Practice history 改为 owner-bound opaque cursor、稳定复合排序与 `limit + 1` nextCursor。

## 5. 只读静态证据

- Java 文件：394。
- duplicate FQN：0。
- package/path mismatch：0。
- 项目内部普通 import 缺失：0。
- domain/application 对 Spring/JPA/adapter/boot 的反向依赖：0。
- application 跨逻辑域 `internal` import：0。
- Interview/Evaluation 领域事件注册覆盖：31/31；额外 3 个字符串是公开事件类型。
- Migration 版本重复：0；当前连续 V001–V010。
- backend/contracts 中 PaiCLI 引用：0。

以上只是 `rg`/文件结构/文本级检查。未运行编译、构建、测试、Migration、数据库、Spring Context、
服务或浏览器，因此所有运行证据仍为 `NotRun`。

## 6. 后续必须承接

- 窗口 24：实现 owner 授权后的 replay endpoint、`Last-Event-ID`、410/snapshot、SSE `id:` 与
  live fan-out。`interview.question.committed` 的公开契约要求 question text，而 durable data
  有意不保存 Prompt 正文；入站 adapter 必须从 owner-scoped immutable Session snapshot/Turn
  读取并补全，不能把正文写回 stream_event。
- 窗口 29：提供 retention 配置、`StrictDurableStreamEventPolicy`、
  `DomainEventEnvelopePolicy`、`SensitiveEnvelopeCipher` 和 use case/adapter 的显式 Bean 装配；
  缺任一安全 owner 时保持 unavailable/fail-closed。
- Platform：仍缺 Outbox claim/reclaim publisher 编排，以及 Job `FAILED_RETRYABLE → PENDING`、
  过期 lease 恢复和 stream retention Job use case。
- Product/Architecture：replay window/TTL、heartbeat 和 snapshot URL 仍是待批准配置，源码未硬编码数值。
- Identity/Governance：pre-tenant 注册幂等 owner、ConsentPolicyVersion owner/seed、隐私删除和管理员
  审计仍未闭合；当前保持 fail-closed，不创建虚构 public tenant。
- Evaluation Source 仍是 interview-only，而 Practice 已有评估请求路径；必须先批准 source variant，
  不能伪造 Interview ID。

## 7. 明确未执行

- 未新增或运行测试代码。
- 未安装依赖，未运行 Maven/npm、服务、Migration 或数据库。
- 未调用 Provider、ASR/TTS、支付、对象存储或部署系统。
- 未执行 Git。
