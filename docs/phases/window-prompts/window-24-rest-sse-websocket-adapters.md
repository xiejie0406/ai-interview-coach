# 窗口 24 提示词：REST / SSE / WebSocket Adapters

```text
你负责 AI Interview Coach inbound adapter 候选实现，把 contracts 映射到 application use case。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

开始前完整读取：
- C:\Users\admin\.codex\AGENTS.md 与本任务相关 specs。
- 项目根 AGENTS.md、docs/specs/README.md。
- Feature README/tasks 候选、implementation-contract-pack、contracts/README.md。
- Wave 3 索引，以及窗口 22、23、27、28、30、31、32 的最终交接。

事实边界：L3；主阶段仍为阶段 3 WaitingForApproval；本窗口只是 Draft 候选实现，运行证据保持 NotRun，不能推进 Gate、UAT 或发布。

启动条件：
- 窗口 22、23、27、28、30、31、32 已交接，application use case/port、持久化候选与 durable stream replay 已完成契约回填。
- 如果 use case 不存在，只允许写显式 unavailable/501 映射，不允许跨包补实现。

只允许编辑：
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/rest/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/sse/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/websocket/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/security/**

禁止修改：
- domain、application、persistence、boot、POM、contracts、frontend、测试代码、docs/product、docs/phases/phase-*.md。

目标：
- REST 只负责命令/查询和错误 envelope 映射。
- SSE 只负责 server -> browser 的 session/evaluation/progress 事件恢复，不接收客户端命令。
- WebSocket 只负责语音 session 的双向低延迟信令、ticket/generation、双向 sequence、ack/nack、flow-control、cancel、transcript delta；只有 application ticket/Artifact/存储/ASR 端口全部可用时才允许音频帧，否则显式 unavailable。
- 所有入口强制 tenant/principal/csrf/correlation id。
- 缺失公共契约时使用明确 409/422/501，不用 mock success。
- 不暴露 socketTicket、audio object key、prompt/model raw content、candidate answer raw text。
- 以当前 64 个 OpenAPI operation 为路由清单；未实现 use case 返回稳定 unavailable/501，不用空 200/202。
- Interview/Evaluation SSE 必须调用窗口 32 的 durable replay port，支持 `Last-Event-ID`、410 cursor expired 与 REST snapshot；禁止退回 Outbox ID、进程内 sequence 或固定 empty cursor。Report 查询必须实现 owner-scoped interviewId -> reportId，不做 ID 猜测。
- 删除先校验 server challenge/step-up/blocker；金额使用 amountMinor/currency/currencyExponent；Admin 投影只返回固定脱敏 schema。

建议文件：
- rest/identity、rest/catalog、rest/practice、rest/interview、rest/evaluation、rest/learning、rest/billing、rest/governance。
- sse/interview/SessionEventController、sse/evaluation/EvaluationEventController。
- websocket/voice/VoiceWebSocketHandler、VoiceFrameMapper、VoiceFlowControlState。
- security/PrincipalResolver、TenantResolver、CsrfFailureMapper。

只读静态检查：
- adapter 只依赖 application/domain 和 framework。
- 无 PaiCLI 引用。
- 所有返回错误保持统一 envelope。
- 无敏感日志。
- contracts 裂缝单独列出，不在 adapter 猜造。

不得运行构建、测试、服务、迁移、Git 或外部调用。

停止条件：任一上游未交接、目标目录已有 owner、实际契约与冻结输入不一致、需要越界补 use case/port、发现 secret/PaiCLI/敏感日志时，立即停止写入并交给窗口 20。

交接必须列出：
- 实际修改文件与 64 个 OpenAPI operation 映射表。
- durable SSE replay、410/snapshot、WebSocket ticket/generation/sequence 的实现或 unavailable 状态。
- security 入口变化、统一错误映射与仍缺 use case 的 501 路由。
- 窗口 29 必须装配的 Bean/Properties 清单。
- 只读静态检查、契约裂缝、未执行项与 NotRun。
```
