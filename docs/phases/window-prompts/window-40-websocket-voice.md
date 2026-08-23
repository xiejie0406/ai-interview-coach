# 窗口 40 提示词：Voice WebSocket Protocol

```text
你负责 Voice WebSocket 入站协议候选；真实音频上传/ASR/TTS 未授权且默认禁用。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34 已交接；33 授予 inbound/websocket/** 唯一 owner。完整读取用户/项目 AGENTS/specs、voice-events AsyncAPI、voice REST/OpenVoiceSession、Artifact/Transcript/VoiceExecution 状态与窗口 28 交接。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；无真实 socket、对象存储或 Provider 运行证据。

只允许编辑 backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/websocket/**。

必须完成：
1. HTTP upgrade 前重验 same-origin session、tenant/owner、短期 single-use socketTicket、generation 和 consent/preflight；token/ticket 不放日志或持久缓存。
2. 双向 frame 明确 type、generation、client/server sequence、ack/nack、最大 chunk/in-flight/buffer、flow-control pause/resume、cancel、terminal；越界/乱序/重复有稳定结果。
3. ASR partial/transcript delta 是 ephemeral；只有 ConfirmTranscript REST 可产生稳定 AnswerVersion/Session 事实。
4. Artifact/storage/ASR ports 任一缺失时拒绝 upgrade 或返回明确 unavailable；禁止把字节留内存假装已上传、禁止 fake transcript。
5. disconnect/reconnect 不自动重放音频命令；先走 REST snapshot/preflight 新 ticket。

禁止改 REST/SSE/security、domain/application/persistence/boot/contracts/frontend/POM/Migration/测试。
静态检查：所有 frame union、generation/sequence/ack/backpressure/cancel/terminal 映射；无 raw audio/transcript/ticket 日志；无 mock success/PaiCLI。
停止条件同统一规则，尤其缺 ticket/Artifact owner 时停止成功路径。
交接：文件、frame 表、启用/拒绝条件、窗口 43/47 所需契约和 Bean、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、真实音频/外部调用、部署或 Git。
```
