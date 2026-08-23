# 窗口 39 提示词：SSE Durable Replay

```text
你负责 Interview/Evaluation SSE 入站候选，严格消费窗口 32 的 DurableStreamPort。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34 与 32 已交接；33 授予 inbound/sse/** 唯一 owner。完整读取用户/项目 AGENTS/specs、AsyncAPI common/interview/job、RecoverInterview/GetEvaluation、DurableStreamPort/V010/窗口 32 记录。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；live fan-out、heartbeat 数值和运行证据尚未闭合。

只允许编辑 backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/sse/**。

必须完成：
1. 建连先解析 server session，再用 RecoverInterview/GetEvaluation 做 tenant+owner 404；不能先用 cursor 探测资源。
2. 标准读取 Last-Event-ID；调用 replayAfter。VALID 按 sequence 输出，SSE `id:` 使用 ReplayEvent.cursor，envelope `eventId` 仍用业务 eventId。
3. EXPIRED 在 event-stream 建立前返回 410 `STREAM_CURSOR_EXPIRED` 与按 RecoveryTarget allowlist 生成的 snapshot URL；UNKNOWN_OR_FOREIGN 返回不泄漏存在性的统一错误，禁止从头重放。
4. `interview.question.committed` 的 durable data 无 Prompt 正文；从 owner-scoped immutable snapshot Turn 补全 text，不能把正文写回 stream_event 或日志。
5. EPHEMERAL delta/progress 不持久化、不推进状态。live subscription port/heartbeat 未装配时明确 unavailable，不能把一次 catch-up 假装成持续 SSE。
6. disconnect/cancel/backpressure/emitter timeout 有界；每次重连重新鉴权；不在 URL 放 token。

禁止改 REST/WS/security、domain/application/persistence/boot/contracts/frontend/POM/Migration/测试。
静态检查：两条 channel、三态 cursor、410、snapshot、event id/sequence、敏感字段、未知 schema 行为完整；无 Outbox ID/内存计数/PaiCLI。
停止条件：缺 owner use case、live subscription 契约、heartbeat 配置或需改公共契约时停止并交给 33。
交接：文件、SSE 状态表、event 映射、仍需 live fan-out/Boot Bean、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、外部调用、部署或 Git。
```
