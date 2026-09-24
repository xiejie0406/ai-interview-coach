# 窗口 36 提示词：REST Interview / Voice

```text
你负责 Interview Plan/Session 与 Voice REST 入站候选；SSE 和 WebSocket 不在本窗口。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34 已交接；33 授予 rest/interview 与 rest/voice 唯一 owner。完整读取用户/项目 AGENTS/specs、interview-plan.yaml、interview-session.yaml、voice.yaml、application interview/voice 公共接口、窗口 28/32 交接。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；PaiCLI 独立。

只允许编辑：
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/rest/interview/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/rest/voice/**

operation owner：createInterviewPlan、getInterviewPlan、applyInterviewPlanCommand、createInterviewSession、recoverInterviewSession、applyInterviewCommand、submitInterviewAnswer、voicePreflight、openVoiceSession、getTranscript、confirmTranscript、getAudioArtifactStatus、deleteAudioArtifact。

必须完成：
1. Plan create 只接受公开 setup；profile/questionCount/budget/expiry/reservation 由服务端 policy/use case 决定。Confirm 校验 acknowledgedEstimateVersion；客户端不提交 reservationId 或释放原因。
2. Session snapshot 17/17 字段映射；owner 404；mutation 使用 Idempotency-Key/If-Match/expectedVersion；TEXT answer 不能绕过 VOICE ConfirmTranscript。
3. ConfirmTranscript 映射 transcript ETag/version/low-confidence acknowledgement 与可选 user correction；原子 AnswerVersion/Session/Job 由 application port 完成，REST 不拼事务。
4. Voice preflight/open/status/delete 在 consent、ticket、Artifact/ASR/TTS/storage owner 不完整时稳定 unavailable；不伪造 ticket/object key/上传 URL。
5. 任何回答、Transcript、Prompt、音频、session handle、ticket 均不进日志/错误/toString。

禁止改 SSE/WS/common/security、domain/application/persistence/boot/contracts/frontend/POM/Migration/测试。
检查 13 个 operation 全覆盖；状态/错误/owner/version/幂等一致；无 mock success/PaiCLI/敏感日志。
停止条件同 Wave 4 统一规则。
交接：13 operation 表、文件、snapshot/voice 状态表、unavailable 条件、窗口 39/40/47 所需接口、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、外部调用、部署或 Git。
```
