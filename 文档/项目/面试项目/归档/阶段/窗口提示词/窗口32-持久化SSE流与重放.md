# 窗口 32 提示词：Durable SSE Stream / Replay

```text
你负责 AI Interview Coach 的 durable stream_event、SSE cursor 与 replay persistence 候选实现。当前 Interview recovery projection 明确返回 empty cursor，EvaluationStreamCursorPort 也没有实现；在这条缺口关闭前，REST/SSE 窗口不得伪造 Last-Event-ID 恢复。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

启动条件：
- 窗口 22/30 Persistence 已结束，不再编辑 migration 或 outbound persistence/platform。
- 窗口 27/28 已交接最新 Evaluation/Interview 状态与事件。
- 先枚举 migration；若 `V010__durable_stream_events.sql` 已存在，立即停止写入并交给窗口 20 核对 owner/交接，禁止覆盖或创建第二个 V010。
- 先读取实际 `DomainEventPort`、`DomainEventEnvelopePolicy`、Outbox、`EvaluationStreamCursorPort`、`InterviewRecoveryProjectionPort` 和 AsyncAPI；不得从内存 sequence 或浏览器 cursor 推导 durable 状态。

事实边界：
- 项目与 PaiCLI 完全独立。
- L3；主阶段仍为阶段 3 WaitingForApproval；所有运行证据 NotRun。
- stream_event 是用户恢复读模型，不取代 Outbox；Outbox eventId 不能直接冒充长期 SSE cursor。

只允许编辑：
- backend/interview-domain/src/main/java/com/aiinterviewcoach/domain/platform/**（仅最小 stream 值对象）
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/platform/port/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/evaluation/EvaluationStreamCursorPort.java
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/interview/port/InterviewRecoveryProjectionPort.java
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/platform/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/evaluation/**（仅 cursor adapter）
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/interview/JdbcInterviewRecoveryProjectionRepository.java
- backend/interview-boot/src/main/resources/db/migration/V010__durable_stream_events.sql

禁止修改 inbound、frontend、contracts、其他 domain/application/adapters、Boot Java、POM、测试、既有 migration、docs/product、technical-architecture 截断原文和 phase-*.md。

必须完成：
1. `platform.stream_head` 以 tenant_id + stream_type + stream_id 原子分配严格递增 sequence；禁止先查最大值再插入。
2. `platform.stream_event` 保存 event_id、stream、aggregateId/version、sequence、type、occurredAt、schemaVersion、correlationId、allowlisted JSON data、expiresAt；唯一约束包含 tenant 与 stream。
3. DomainEvent 与 Outbox 在同一本地事务中，根据显式 allowlist/policy 同步写 stream_event；未注册事件 fail-closed，不能复制原始 attributes 或正文。
4. Interview、Evaluation 只写它们批准的 durable event；delta、typing、progress hint 不进入 durable 表。
5. 提供 current cursor 与 after Last-Event-ID replay：区分 VALID、EXPIRED、UNKNOWN/FOREIGN；过期返回可映射 410 的稳定结果和 snapshot path owner，不静默从头重放。
6. replay 顺序只按 stream sequence；eventId + sequence 可去重；limit 有上限；跨 tenant/stream cursor 返回不泄漏资源存在性的结果。
7. retention 清理由 Job 候选负责；删除 event 不回退 stream_head，不复用 sequence/eventId。
8. `EvaluationStreamCursorPort` 与 Interview recovery projection 使用真实 stream head/current cursor，不再固定 empty。
9. Outbox publisher、SSE live fan-out 尚未运行时，已提交 durable events 仍可由 replay 查询恢复。

只读静态检查：
- migration 版本唯一，tenant/stream/sequence/eventId/FK/index/retention 完整。
- sequence 分配是数据库原子语句或锁定 head，不是 Java 进程锁。
- data 只含 ID、版本、状态、计数、原因码；无 answer/transcript/prompt/model/payment/object key。
- domain/application 无 Spring/JPA/adapter 反向依赖；无 PaiCLI。

不得运行构建、测试、迁移、数据库、服务、Provider 或 Git。

交接：修改文件、事件 allowlist、cursor/replay/expired 状态表、仍需 REST SSE 承接的端口、NotRun。
```
