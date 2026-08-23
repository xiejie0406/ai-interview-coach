# 第 13 期：TTS 与级联语音面试闭环

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-13  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：voice / interview / agent / integration / frontend  
> 证据结果：NotRun  
> 前置：PHASE-02、06、09、12；TTS 与语音轮次原型批准

## 1. 本期为什么存在

将 ASR 输入、既有文本 Interview Agent 和 TTS 输出组合，形成 T1 的级联语音闭环；复用同一 Session/Evaluation，避免语音成为平行业务系统。

## 2. 用户可见目标

用户完成一场短语音面试：听到问题、回答并确认转写、听到追问；任一语音失败时文本仍可见/可答，已完成轮次不丢。

## 3. 技术学习目标

学习 TTS streaming、语音 UI 状态、半双工轮次编排、播放取消、文本优先降级、端到端延迟/成本观测。

## 4. 范围

TextToSpeechPort、TTS Adapter、首包/分片/完成/取消、VoiceTurnController、LISTENING/TRANSCRIBING/THINKING/SPEAKING/DEGRADED、停止播放、文本 fallback、语音用量结算、表达指标限定输入。

## 5. 非目标

不做 Realtime/WebRTC、全双工自由抢话、声音克隆、特定人物音色、视频/情绪/人格分析或语音专属状态机。

## 6. 前置决策

DEC-008/036/043/048/059/068；中性声音、缓存、首段延迟口径、用户打断只停播放还是结束轮次、TTS 数据地域。

## 7. 前置期次和依赖

02 TTS 风险；06 SPI；09 文本闭环；12 ASR/Artifact。必须复用 07 Reservation、08 Job/Session/SSE。

## 8. 涉及的 REQ/BR/AC/DES

REQ-05/09/15；BR-05–07/09；AC-05/06；DES-VOICE-TTS/CASCADE、DES-STATE-VOICE-UI。

## 9. 本期完整功能点

问题文本先可见；TTS 生成/播放；语音轮次 UI；用户停播/重播候选；ASR→Agent→TTS 编排；失败/超时/中断转文本；TTS 用量/成本；可访问字幕/音量/键盘。

## 10. 正常流程

Committed question 文本→TTS Job/stream→播放→LISTENING→ASR/确认→文本 Agent→下一 question→TTS；结束后复用 Phase 10 报告，语音指标只来自批准事实。

## 11. 空态、错误、拒绝、取消和恢复

无音频输出设备仍显示文本；TTS 拒绝/限流/中途断开立即 DEGRADED；用户静音/停播不推进业务；断线用 snapshot 重建，过期音频可重新合成但幂等结算；取消会话走同一 command。

## 12. 后端模块

application `TextToSpeechPort/SynthesizeSpeech/RunVoiceInterview/VoiceTurnPolicy`；adapters TTS/voice WS output；domain 只新增必要 speech artifact ref，不改 Session 状态。

## 13. 前端页面和组件

同 `/app/interviews/:id` 的 voice mode；`AudioPlayer`、`VoiceTurnStatus`、`VoiceControls`、`TranscriptEditor`、`TextFallbackPanel`、`useSpeechStream/useVoiceInterview`、voice UI store。

## 14. 数据实体、约束和迁移

可新增 `voice.tts_artifact/tts_invocation`；input text hash、voice config version、tenant/private cache scope、expiresAt、usage；业务 Question/Turn 不依赖 TTS success。跨 tenant 缓存仅公开固定内容且单独批准。

## 15. REST/SSE/WebSocket/API 或事件

复用 voice WS，增加 `tts.chunk|complete|cancelled|failed`、`voice.turn.state`；REST text fallback/command 仍有效；SSE 继续业务状态，WS 只传语音事件。

## 16. Agent/Prompt/Provider

`TextToSpeechPort`；Interview Agent 不感知供应商/播放状态；TTS 输入只用已提交问题文本。主备/缓存/重试受成本和 data-region。

## 17. 安全与隐私

TTS 文本可能含用户上下文，按 Confidential；短期 Artifact、私有访问、删除；声音许可/不克隆；日志不记合成文本/音频。

## 18. 计划新增文件树

```text
interview-application/.../agent/port/TextToSpeechPort.java
interview-application/.../voice/{SynthesizeSpeech,RunVoiceInterview,VoiceTurnPolicy}.java
interview-adapters/.../{provider/tts,inbound/websocket/voice/output,persistence/voice}/
interview-boot/.../db/migration/V###__create_tts_artifact.sql
frontend/src/features/voice/output/{AudioPlayer,VoiceTurnStatus,VoiceControls}.tsx
frontend/src/features/voice/hooks/{useSpeechStream,useVoiceInterview}.ts
contracts/asyncapi/voice-output.yaml
```

## 19. 计划修改文件树

```text
frontend/src/features/interview/room/TextInterviewPage.tsx
frontend/src/features/voice/store/voiceUiStore.ts
interview-application/.../billing/{SettleUsage,ReleaseUsage}.java
interview-boot/.../ProviderConfiguration.java
contracts/openapi/voice.yaml
```

## 20. 后续 TASK 拆分建议

TTS port/adapter、artifact/cache、WS output、turn orchestration、UI/accessibility、fallback/idempotency、真实延迟/成本/UAT；不混入 Realtime。

## 21. 验证建议

单元：VoiceTurnPolicy；Provider contract；集成：Job/usage/artifact；WS contract；浏览器：播放/拒绝/断线；Golden Set：发音可懂度/术语；UAT：完整短语音与文本降级。fake 不证明延迟/声音质量。

## 22. 明确完成标准

批准环境下完成 ASR→Agent→TTS；文本始终可用；TTS 失败不推进/丢 Session；重试不重复结算；可访问控制有效；有实际 EV 后 T1 级联语音闭环成立。

## 23. 本期不能证明什么

不能证明 Realtime、自然打断、所有设备/网络、生产容量、付费提升或长期音频合规。

## 24. 风险与停止条件

文本 fallback 不完整、TTS 控制业务状态、缓存跨 tenant 泄露、无法满足地域/声音许可/成本门或为了延迟自动转 Realtime 时停止。

## 25. 下一期进入条件

文本与级联语音 T1 均可用；多实例/故障/限流深化可在不改业务契约下进行。

## 26. 建议学习和复盘内容

复盘 cascade voice architecture、half-duplex UX、audio streaming、playback cancellation、graceful degradation 与体验/成本权衡。
