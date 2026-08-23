# 窗口 43 提示词：Frontend Interview / Voice

```text
你负责 Interview Plan/Session 与 Voice UI 候选；真实麦克风上传默认禁用。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：41 已交接；33 授予 interview/**、voice/** 唯一 owner。完整读取冻结 Interview/Voice OpenAPI、AsyncAPI、窗口 28/32 记录，以及 36/39/40 handoff（若已完成，不能读取进行中实现）。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；无浏览器/设备/服务运行证据。

只允许编辑 frontend/src/features/interview/** 与 voice/**。

必须完成：
1. Plan setup→estimate→acknowledge→confirm/cancel；客户端不提交 reservationId、价格/预算权威或服务端原因。
2. Session 先 GET 17 字段 snapshot，清理过期 optimistic action，再以 streamCursor 建 SSE；410 时停止消费→重新 snapshot→新 generation 重连，不从头猜放。
3. turn list/current question/submit/skip/pause/resume/finish/cancel 只按 allowedCommands；Idempotency key/ETag 重试不产生第二命令。
4. SSE 用返回 Last-Event-ID，unknown schema 安全忽略并诊断；delta 可撤销，question committed 才是事实。
5. Voice 必须 consent+preflight+server handle+single-use ticket 全部成功才允许 WebSocket/Recorder；任一 501/拒绝/设备 denied 自动降级文本。
6. ticket、raw audio、answer、transcript 不落 local/session/IndexedDB；partial 仅内存；ConfirmTranscript 显示低置信度与 user correction，不能绕过。
7. generation/sequence/ACK/NACK/in-flight/backpressure/cancel/terminal 映射到可恢复 UI；断线先 snapshot，不自动重发音频。

禁止编辑 app/shared/其他 feature/backend/contracts/config/dependency/测试。
检查主流程及 loading/empty/error/denied/cancel/recover/degraded；敏感存储为 0；无 mock upload/PaiCLI。
停止条件同统一规则。
交接：文件、状态机、SSE/WS 恢复表、默认禁用条件、46 请求、NotRun。
不得修改依赖、安装、构建/测试、浏览器/麦克风、服务、外部调用、部署或 Git。
```
