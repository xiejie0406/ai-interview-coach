# 窗口 28 提示词：Interview Snapshot / Voice Answer 原子收口

```text
你负责 AI Interview Coach 的 Interview 权威恢复快照与 Voice Transcript 确认后提交回答的原子链路收口。不要把候选代码、静态检查或文件存在描述为 Approved、Pass 或已运行。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

启动条件：
- 窗口 23 已完成 Core Application 交接。
- 窗口 31 已完成 Core Application 边界与幂等收口。
- 窗口 27 已完成 Evaluation/Report/Learning 契约收口，Report 映射与状态不再变化。
- 若上述任一窗口仍在写相同目录，立即停止，避免并发覆盖。

事实边界：
- 项目与 PaiCLI 完全独立。
- 风险为 L3；主阶段仍是阶段 3 WaitingForApproval；运行证据全部是 NotRun。
- PRD 与 technical-architecture 存在截断，禁止修改或猜补。
- 以 contracts/openapi/interview-session.yaml、contracts/openapi/voice.yaml、contracts/asyncapi/interview-events.yaml 和现有 application/domain 状态机为实现输入。

只允许编辑：
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/interview/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/voice/**

禁止修改：
- domain、adapters、boot、contracts、frontend、POM、migration、测试代码、docs/product、docs/architecture/technical-architecture.md、docs/phases/phase-*.md。

必须完成 A：InterviewSessionSnapshot 对齐
1. Snapshot 必须完整表达 OpenAPI 的 id、planId、planVersionNo、planContentHash、mode、state、turns、lastStableTurnSequence、pendingJobIds、reservation、report、voiceSummary、allowedCommands、streamCursor、recoveryExpiresAt、failureCode、version。
2. planVersionNo 与 planContentHash 来自创建 Session 时固定的不可变 PlanVersion；不得在恢复时读取当前激活计划或重新计算为另一个版本。
3. pendingJobIds 是去重、稳定排序的列表，不能继续缩减成单个 pendingJobId。
4. Reservation、Report 与 Voice 只使用最小投影 DTO；Interview 不读取 Billing/Evaluation/Voice 的 repository 实现，不把 interviewId 当作 reportId。
5. turns 显式包含 kind/state，以及可选 questionText/answerVersionId；快照装配不能制造尚未提交的问题或回答。
6. streamCursor 代表可恢复的 durable cursor；不能用浏览器本地 sequence 或瞬时内存计数替代。
7. failureCode 使用稳定原因码，不暴露 Provider、Prompt、回答、Transcript 或内部异常正文。
8. 所有查询必须 tenant + owner scoped；找不到与越权对外均保持不泄漏资源存在性的语义。

必须完成 B：Confirm Transcript 与 Interview Answer 原子提交
1. `ASR_FINAL` 不是回答；只有用户 confirm/correct 后才允许形成 AnswerVersion。
2. 同一本地事务必须依次完成：重验 principal/tenant/owner、Transcript ETag、最新 TranscriptVersion、低置信度确认；可选追加 USER_CORRECTION；确认 TranscriptVersion；把确认文本以 VOICE_TRANSCRIPT source 提交为不可变 Interview AnswerVersion；推进 Turn/Session 稳定点；创建 next-step Job/Outbox；保存两个聚合与幂等结果。
3. 任一步失败必须整体回滚；禁止出现 Transcript 已 CONFIRMED 但 AnswerVersion/next Job 缺失的部分成功。
4. 重复 Idempotency-Key 必须回放同一个 transcriptId、confirmedTranscriptVersionId、answerVersionId、nextStepOperation 和版本结果，不得重复创建回答、Job 或 Outbox。
5. `ConfirmTranscript.Result` 必须对齐 ConfirmedTranscriptView：transcriptId、confirmedTranscriptVersionId、answerVersionId、nextStepOperation、transcript version。
6. OpenAPI 没有额外的 Session If-Match；服务端必须在事务内读取并重验 Session/Turn 当前状态。若会话已推进、暂停、取消或完成，返回稳定 409/拒绝原因，不得隐式覆盖。
7. 使用 application 内部的 consumer-owned bridge/port 或已有 SubmitInterviewAnswer 边界完成跨子域协作；不得让 Voice 读取 InterviewRepository，不得反向依赖 adapter。
8. answer/transcript/correctedText 不进入 toString、异常 metadata、Domain Event 或 Outbox 正文；事件只携带稳定 ID、hash、版本、状态和原因码。

只读静态检查：
- InterviewSnapshot 字段与 OpenAPI required/properties 一一映射。
- ConfirmTranscript 结果与 OpenAPI ConfirmedTranscriptView 一一映射。
- 不存在只确认 Transcript 的生产实现路径。
- package/path/import 可定位；application 不依赖 adapters/boot/framework。
- tenant/owner/version/idempotency/transaction/job/outbox 均有明确 owner。
- 无 PaiCLI 引用、无敏感 toString。

不得运行 Maven/npm 构建、测试、服务、迁移、真实 Provider/ASR/TTS、Git 或外部调用。

交接必须列出：
- 修改文件。
- 旧 Snapshot 字段到新字段的映射。
- Confirm Transcript 原子事务步骤与失败回滚点。
- 仍需 Persistence、REST、SSE、WebSocket 或 Boot owner 实现的端口。
- 未执行项与证据状态 NotRun。
```
