# 第 09 期：文本 Interview Agent 闭环

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-09  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：interview / agent / integration / frontend  
> 证据结果：NotRun  
> 前置：PHASE-06、07、08

## 1. 本期为什么存在

将已验证 SPI 和确定性会话组合为第一个可用文本面试纵切，是 T1 核心学习与产品价值门；后续评测、语音都复用此闭环。

## 2. 用户可见目标

用户完成一场短文本面试：流式看到问题、回答、有限相关追问，可暂停/跳过/结束/刷新恢复；失败时已答内容保留。

## 3. 技术学习目标

学习 Agent orchestrator、结构化动作→领域命令、流式 token vs 业务事件、上下文预算、Provider 降级和端到端异步前端。

## 4. 范围

Interview Agent、Prompt/Schema、ContextBuilder、追问/时间门、主/备/失败策略、文本 room、REST command+SSE delta/state、调用/用量记录。

## 5. 非目标

不生成正式综合报告、不接语音、不做无界 ReAct/工具执行/多 Agent、不支持真实面试 Copilot。

## 6. 前置决策

DEC-011/013/030/034/038/058；文本首响应候选 SLO、追问上限、上下文保留和部分结束语义批准。

## 7. 前置期次和依赖

06 SPI/Prompt；07 Plan/Reservation；08 Session/Job/SSE/recovery。不得建立第二套会话/事件/成本事实。

## 8. 涉及的 REQ/BR/AC/DES

REQ-03/04/06/09/12/15；BR-01–04/09/10；AC-03/09/12；DES-AGENT-INTERVIEW、DES-API-SSE、DES-STATE-SESSION。

## 9. 本期完整功能点

结构化 ASK/FOLLOW_UP/CLARIFY/NEXT/COMPLETE；范围/预算门；Prompt context；delta/state 事件；回答提交；暂停/继续/跳过/结束；刷新/Provider 失败恢复；反作弊用途拒绝文案。

## 10. 正常流程

Start→Job 调 Interview Agent→schema/门禁→问题先落库再 SSE→用户提交 AnswerVersion→Agent 生成下一候选→预算裁决→结束到 COMPLETING。

## 11. 空态、错误、拒绝、取消和恢复

无题/计划过期拒绝开始；坏输出进入受控失败；Provider timeout 限次重试/备切换；内容安全拒绝提示；用户暂停/取消；SSE 中断 snapshot 恢复；已落库问题不重复生成。

## 12. 后端模块

application `InterviewAgent/ContextBuilder/RunTextInterview/SubmitTextAnswer`；adapters LLM/REST/SSE；domain 仍由 interview owner；platform Job 调度。

## 13. 前端页面和组件

`/app/interviews/:id`；`TextInterviewPage`、`AnswerComposer`、`InterviewTimeline`、`SessionControls`、`ConnectionStatus`、`ProviderFailureNotice`；hooks/store 复用 08。

## 14. 数据实体、约束和迁移

复用 session/turn/provider_invocation/reservation；可新增 `agent.context_snapshot_ref` 或 prompt invocation reference，不保存不必要完整 Prompt；问题/回答顺序和 input version 固定。

## 15. REST/SSE/WebSocket/API 或事件

REST answer/commands/snapshot；SSE `interview.question.delta|committed`, `agent.status`, `session.state`, `usage.updated`, error/terminal；delta 不作为事实，committed/snapshot 是事实；无 WS。

## 16. Agent/Prompt/Provider

Interview Agent 只输出候选动作；Plan/Session/Entitlement 裁决。主备路由受 data-region/Flag/成本；Prompt/Schema 版本随 Turn，禁止自由文本驱动状态。

## 17. 安全与隐私

最小上下文、tenant stream auth、Prompt injection 防护、日志不记完整回答；前端不持 Provider Key；明确拒绝隐蔽代答场景。

## 18. 计划新增文件树

```text
interview-application/.../agent/interview/{InterviewAgent,ContextBuilder,ActionGuard}.java
interview-application/.../interview/runtime/{RunTextInterview,SubmitTextAnswer}.java
interview-adapters/.../{provider/llm,inbound/rest/interviewtext,inbound/sse/interview}/
interview-boot/src/main/resources/prompts/interviewer/v1/
frontend/src/features/interview/room/{TextInterviewPage,AnswerComposer,InterviewTimeline,SessionControls}.tsx
frontend/src/features/interview/hooks/useInterviewEvents.ts
contracts/{openapi/text-interview.yaml,asyncapi/text-interview-events.yaml,schemas/interviewer-action-v1.schema.json}
```

## 19. 计划修改文件树

```text
interview-application/.../interview/session/
interview-application/.../billing/{ReserveUsage,SettleUsage}.java
frontend/src/app/router.tsx
benchmarks/fixtures/interview-actions/
```

## 20. 后续 TASK 拆分建议

Agent/context/guard、Job 编排、SSE 事件、Provider 策略、Room UI、恢复/反作弊、质量/延迟验证；真实调用授权单列。

## 21. 验证建议

单元：动作/预算；集成：Session+Job+Reservation；Provider contract；API/SSE 契约；UI/Playwright：主流程/断线/暂停；Golden Set：追问相关性；UAT：短面试。真实 Provider质量需授权。

## 22. 明确完成标准

批准的短计划能端到端走完；追问不越界/超预算；问题落库后才呈现事实；断线/超时不丢已答；文本闭环可观察并有实际 EV 后完成。

## 23. 本期不能证明什么

不能证明评分/报告正确、语音体验、长会话容量、商业转化或生产 SLO。

## 24. 风险与停止条件

Agent 能直接改状态/额度、SSE delta 被当事实、上下文无界、主备循环、Prompt 版本不可定位或出现 Copilot 能力时停止。

## 25. 下一期进入条件

完成/主动结束会话产生稳定 Answer/Turn 输入与 Evaluation Job；文本 UAT 语义无阻断歧义。

## 26. 建议学习和复盘内容

复盘 agent orchestration vs autonomy、streaming semantics、context budgeting、provider fallback、latency instrumentation 与纵切验收。
