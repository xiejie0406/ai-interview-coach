# Provider adapters wave（2026-08-09）

## 状态

- 风险等级：L3
- 项目主阶段：阶段 3 功能规格，`WaitingForApproval`
- 本轮局部阶段：阶段 7 开发实现，`InProgress`
- 运行证据：`NotRun`

## 本轮范围

本轮只实现已批准的本地生产源码和配置：DeepSeek Chat Completions、火山豆包流式 ASR 2.0、火山豆包 TTS 2.0 的供应商边界。未实现业务 REST/SSE/WebSocket、面试 Agent 编排、前端录音链路或对象存储下载实现。

## 已实现

- 新增 `DeepSeekChatModelAdapter`：结构化 JSON 输出、请求/响应大小限制、超时、稳定失败分类、用量读取和 request ID 哈希；请求仅允许配置中的面试与评测模型，并显式发送 Thinking Mode。
- 新增火山语音 profile/resource 映射与公共失败处理。
- 新增 `VolcengineTextToSpeechAdapter`：单向流式 TTS 请求、音频分块写入 `AudioSink`、响应上限、用量与失败映射。
- 新增 `VolcengineSpeechToTextAdapter`：流式 ASR WebSocket 协议边界、音频分块、响应上限和稳定失败映射。
- 新增窄接口 `AudioArtifactSource`。ASR 只有在存在受控音频读取实现时才装配；当前项目尚无该实现，因此保持 fail-closed。
- 在 `ProviderConfiguration` 中接入真实 adapter；所有 adapter 继续受 `external-provider-calls-enabled` safety gate 控制，默认仍使用 Disabled adapter。
- 增加 DeepSeek、火山语音的环境变量模板和 Spring 配置绑定。Secret 均为空，不进入源码或文档。

## 未执行与未发生

- 未新增或修改测试代码。
- 未执行 Maven/npm 构建、测试、启动或安装依赖。
- 未使用真实 API Key，未调用 DeepSeek 或火山引擎。
- 未读取、上传或发送真实音频。
- 未部署，未执行 Git 暂存、提交或推送。

## 已知阻塞与后续

- `FoundationSafetyProperties` 仍强制外部 Provider 开关为 false；真实联调需要单独批准后解除阶段锁。
- ASR 仍缺少 tenant-scoped 的 `AudioArtifactSource` 实现，不能从 `ArtifactRef` 取得音频正文。
- 火山 TTS 仍需要填写已批准的音色 ID。
- 完整语音面试还需要业务入口、实时事件通道、面试 Agent orchestration、录音/对象存储链路和端到端验收。
