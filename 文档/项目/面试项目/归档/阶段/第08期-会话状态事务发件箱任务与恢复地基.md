# 第 08 期：会话状态、Outbox/Job 与恢复地基

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-08  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：interview / platform / frontend  
> 证据结果：NotRun  
> 前置：PHASE-03、07；状态/取消/恢复原型批准

## 1. 本期为什么存在

首场真实 Agent 面试前，先用确定性模拟器验证 Session/Turn、事务 Outbox、Job lease、幂等和恢复。这样 Agent/语音只是外部工作，不成为业务状态事实。

## 2. 用户可见目标

在模拟问题模式下，用户可开始、提交、暂停、继续、跳过、主动结束、取消和刷新恢复；Job 状态与失败下一步可见。

## 3. 技术学习目标

学习状态机、乐观锁、Outbox、lease worker、至少一次投递、幂等键、恢复快照、SSE cursor 和异步 UI。

## 4. 范围

InterviewSession/Turn/command、状态/预算、Outbox/Job/IdempotencyRecord、Worker claim/lease、恢复快照、模拟 next-question adapter、基础 SSE 状态事件。

## 5. 非目标

不调用 LLM、不生成正式评测/报告、不接音频、不引入 Redis/Kafka、不承诺 exactly-once 或多实例容量。

## 6. 前置决策

主动结束是否部分报告、取消数据保留、恢复窗口、SSE 重放窗口、Job 重试分类；DEC-027/058/062。

## 7. 前置期次和依赖

03 tenant/principal；07 confirmed plan/reservation。后续 09–16 都依赖本期，不能后补。

## 8. 涉及的 REQ/BR/AC/DES

REQ-03/04/06/09/14；BR-02/09；AC-03/09；DES-STATE-SESSION/JOB、DES-ASYNC-OUTBOX、DES-IDEMPOTENCY。

## 9. 本期完整功能点

状态迁移；Turn 序号/稳定点；幂等命令；事务 Outbox/Job；worker lease/heartbeat/接管；模拟问题；SSE 状态/heartbeat/终态；恢复 snapshot 与前端恢复引导。

## 10. 正常流程

Confirmed Plan→创建 READY session/Job→start→模拟问题落库并推送→提交回答同事务写 Turn+Outbox→pause/resume→complete→释放/保留 Reservation 按批准规则。

## 11. 空态、错误、拒绝、取消和恢复

无 READY session 显示空态；非法迁移/陈旧 version 拒绝；重复命令返回原结果；用户取消明确影响；worker 中断 lease 到期接管；SSE 断线用 Last-Event-ID 续传，超窗则 GET snapshot。

## 12. 后端模块

domain `InterviewSession/InterviewTurn/SessionPolicy/Job/RetryPolicy`；application session commands/recovery/job/outbox ports；adapters persistence/SSE/fake interviewer；boot worker profile。

## 13. 前端页面和组件

`/app/interviews/:id/simulator`；`SessionControls`、`InterviewTimeline`、`JobStatus`、`ReconnectBanner`、`RecoveryDialog`；`useInterviewEvents/useInterviewRecovery/interviewUiStore`。

## 14. 数据实体、约束和迁移

`interview.session/turn/session_event`；`platform.outbox_event/job/idempotency_record`。sequence 唯一；session version 乐观锁；claim lease；同 key 不同 payload 冲突；payload 用引用/最小字段。

## 15. REST/SSE/WebSocket/API 或事件

REST create/start/answers/commands/snapshot/jobs；SSE `/streams/interviews/{id}` with eventId/sequence/heartbeat/terminal；事件 `session.*`, `turn.confirmed`, `job.*`；无 WS。

## 16. Agent/Prompt/Provider

使用 `DeterministicInterviewerFake`，明确不能证明 LLM。真实 Interview Agent 从 Phase 09 接入相同 application port。

## 17. 安全与隐私

session/stream 每次验证 tenant/resource owner；SSE 不用 URL token；Outbox/Job 不复制完整回答；审计命令结果和 correlation ID。

## 18. 计划新增文件树

```text
interview-domain/.../interview/{InterviewSession,InterviewTurn,SessionPolicy}.java
interview-domain/.../platform/{Job,JobStatus,RetryPolicy}.java
interview-application/.../{interview/session,platform/{job,outbox,idempotency}}/
interview-adapters/.../{persistence/platform,inbound/rest/interview,inbound/sse,provider/fake}/
interview-boot/.../{worker,db/migration/V###__create_session_outbox_job.sql}
frontend/src/features/interview/{room,hooks,store}/
contracts/{openapi/interview-session.yaml,asyncapi/{interview-events,job-events}.yaml}
```

## 19. 计划修改文件树

```text
interview-application/.../billing/ReserveUsage.java
frontend/src/app/router.tsx
frontend/src/shared/recovery/
contracts/schemas/event-envelope.schema.json
```

## 20. 后续 TASK 拆分建议

状态机、迁移/事务、Outbox/Job worker、幂等、REST/SSE、模拟 UI、恢复/故障验证；压力/多实例明确留 14。

## 21. 验证建议

单元：全迁移矩阵；集成：事务/lease/重复/tenant；契约：REST/SSE；UI：断线/刷新/取消；故障注入：worker crash；Golden Set NotApplicable；UAT：模拟会话恢复。不能证明 Provider。

## 22. 明确完成标准

非法迁移确定拒绝；已确认回答不丢不重；业务事实与 Outbox 原子；lease 可接管；刷新恢复最近稳定点；重复命令不重复推进/扣减。

## 23. 本期不能证明什么

不能证明 Agent 质量、语音、报告、Redis 多实例、生产吞吐或真正 exactly-once。

## 24. 风险与停止条件

状态语义未批准、Outbox 非同事务、consumer 非幂等、恢复依赖客户端真相、回答进入事件正文或有人建议先上 Kafka 时停止。

## 25. 下一期进入条件

`InterviewerPort` 可替换 fake；REST/SSE/快照和 session command 契约稳定。

## 26. 建议学习和复盘内容

复盘 state machine、transactional outbox、lease/heartbeat、at-least-once、idempotency scope、SSE replay 和恢复 UX。
