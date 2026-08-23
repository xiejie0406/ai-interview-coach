# 第 12 期：音频 Artifact、ASR 与转写确认

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-12  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：voice / governance / integration / frontend  
> 证据结果：NotRun  
> 前置：PHASE-02、03、08；ASR/地域/删除 SLA/语音原型批准

## 1. 本期为什么存在

语音输入涉及麦克风、外部处理、临时对象和术语错写。必须把同意、Artifact 生命周期、ASR、转写确认和删除做成一个纵切，不能先上传后补隐私。

## 2. 用户可见目标

用户看到用途/供应商/留存说明后授权麦克风，完成一个回答录音，看到 partial/final、修正低置信术语并确认；拒绝/失败可立即切文本。

## 3. 技术学习目标

学习 MediaDevices/Web Audio、WebSocket framing/backpressure、ASR port、object storage、artifact lifecycle、consent enforcement 和 transcript versioning。

## 4. 范围

麦克风/设备、音频格式/大小/时长、上传/WS、AudioArtifact、私有对象存储、ASR、partial/final/置信/热词、TranscriptVersion、确认、删除 Job/SLA、文本降级。

## 5. 非目标

不做 TTS、全双工/Realtime、说话人识别、情绪/人格、默认原音回放、长期离线录音或声音训练。

## 6. 前置决策

DEC-008/012/026/035/045/048；原始音频具体 SLA、失败保留、供应商地域/DPA/非训练/删除、codec/VAD/手动结束 UX。

## 7. 前置期次和依赖

02 术语/Provider 风险；03 consent/tenant；08 Session/Job/recovery。Voice 通过 application command 形成 AnswerVersion。

## 8. 涉及的 REQ/BR/AC/DES

REQ-05/06/11/15；BR-06/07；AC-04/05/06；DES-VOICE-ASR/ARTIFACT、DES-PRIVACY-AUDIO、DES-API-WS。

## 9. 本期完整功能点

前置同意；设备检测/拒绝；录音/停止；上传鉴权/序号/背压；Artifact 元数据/TTL；ASR Adapter；partial/final；术语/低置信修正；确认新 TranscriptVersion；删除排队/状态；文本 fallback。

## 10. 正常流程

显示告知→用户同意并授权→打开 turn-scoped WS/upload→录音→ASR partial/final→用户修正确认→生成 AnswerVersion→立即排队删除原音→显示状态。

## 11. 空态、错误、拒绝、取消和恢复

无设备/拒绝时零上传并切文本；格式/过长/乱序拒绝；用户取消录音删除未完成 Artifact；断线 partial 丢弃/重录；ASR 超时保留短期对象按批准规则；删除失败显示/告警而非完成。

## 12. 后端模块

voice domain `AudioArtifact/TranscriptVersion/AudioRetentionPolicy`；application `OpenAudioSession/TranscribeAudio/ConfirmTranscript/DeleteAudioArtifact`；adapters WS/ASR/storage；Job worker。

## 13. 前端页面和组件

文本 room 下 voice input：`MicrophoneConsent`、`DeviceCheck`、`Recorder`、`RecordingStatus`、`TranscriptEditor`、`LowConfidenceTerm`、`VoiceFallbackNotice`；`useVoiceSocket/voiceUiStore`。

## 14. 数据实体、约束和迁移

`voice.audio_artifact/transcript/transcript_version/asr_invocation`；artifact tenant/session/turn/purpose/codec/hash/bytes/duration/storageKey/consentVersion/expiresAt/state；对象正文不入 DB；确认版本不可变。

## 15. REST/SSE/WebSocket/API 或事件

REST consent preflight、open voice session、confirm transcript、artifact status；WS voice input 首帧 auth/turn token/codec，audio chunk sequence、partial/final/error/complete；事件 `audio.delete.requested/completed/failed`。

## 16. Agent/Prompt/Provider

`SpeechToTextPort` 与厂商 Adapter；术语 hints 是版本化配置；ASR output 不直接评分，必须用户确认。LLM 不参与同意/删除。

## 17. 安全与隐私

同意前零采集/零对象/零调用；私有 bucket、短签名、服务端 scope；最小保留/加密/审计；日志不记音频/完整转写；内容管理员无权访问。

## 18. 计划新增文件树

```text
interview-domain/.../voice/{AudioArtifact,TranscriptVersion,AudioRetentionPolicy}.java
interview-application/.../voice/{OpenAudioSession,TranscribeAudio,ConfirmTranscript,DeleteAudioArtifact}.java
interview-application/.../agent/port/SpeechToTextPort.java
interview-adapters/.../{inbound/websocket/voice,provider/asr,provider/storage/audio,persistence/voice}/
interview-boot/.../db/migration/V###__create_voice_artifact_transcript.sql
frontend/src/features/voice/input/{components,hooks,store}/
contracts/{openapi/voice.yaml,asyncapi/voice-input.yaml}
```

## 19. 计划修改文件树

```text
interview-application/.../interview/runtime/SubmitAnswer.java
interview-application/.../governance/GrantConsent.java
frontend/src/features/interview/room/TextInterviewPage.tsx
interview-boot/.../ProviderConfiguration.java
benchmarks/fixtures/speech-terms/
```

## 20. 后续 TASK 拆分建议

隐私/Artifact、browser recorder、WS、storage、ASR、transcript/confirm、delete Job、fallback、真实术语/删除验证；真实音频/费用单列。

## 21. 验证建议

单元：lifecycle/retention；集成：tenant/storage/job；WS contract：乱序/背压；UI/浏览器：权限拒绝/设备；Golden Set：术语/低置信；UAT：语音输入/修正/降级；隐私：同意前零采集。fake 不证明 ASR 质量。

## 22. 明确完成标准

批准真实链路下完成一个语音输入；拒绝时零外传；确认文本才评分；原音按 SLA 进入删除并可观察；失败无损转文本。

## 23. 本期不能证明什么

不能证明完整语音面试/TTS、自然打断、所有浏览器、法律合规或生产延迟/规模。

## 24. 风险与停止条件

DPA/地域/删除不满足、未同意仍上传、Artifact 无 owner/TTL、ASR 低置信直接评分、日志泄漏或需要长期保留原音时停止。

## 25. 下一期进入条件

Confirmed TranscriptVersion 可与文本 Answer 共用 Session/Evaluation；语音 output 不需要改变业务状态或隐私事实。

## 26. 建议学习和复盘内容

复盘 browser audio、WS backpressure、ASR confidence、artifact metadata、consent enforcement、data minimization 和删除证据。
