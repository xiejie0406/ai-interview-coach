# 窗口 25 提示词：Frontend Contract Alignment

```text
你负责 AI Interview Coach 前端契约对齐候选实现。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

启动条件：
- 窗口 22、23、27、28、30、31、32 已交接，OpenAPI/AsyncAPI/Schema 与 application DTO 已冻结为本轮输入。
- 可与窗口 24 并行，但只读取冻结 contracts 与上游交接，不读取或追随窗口 24 的进行中实现；窗口 24 交接后再做一次只读差异回看。
- 本窗口启动后登记 frontend/src/** 唯一 owner；若输入继续变化或目录已占用，停止并交给窗口 20。

事实边界：L3；主阶段仍为阶段 3 WaitingForApproval；本窗口只是 Draft 候选实现，运行证据保持 NotRun。

只允许编辑：
- frontend/src/**
- frontend/package.json
- frontend/vite.config.ts
- frontend/tsconfig*.json

禁止修改：
- backend、contracts、POM、测试代码、docs/product、docs/phases/phase-*.md。

目标：
- 对齐当前 REST/SSE/WebSocket 候选契约，删除或隔离已经明确不存在的 API 调用。
- 所有页面必须有 loading、empty、error、unauthorized/forbidden、conflict、recover 状态。
- 不在 localStorage/sessionStorage/IndexedDB 持久化 token、socketTicket、音频 Blob、candidate raw answer、transcript raw text。
- Voice UI 默认禁用真实上传；只有 consent + preflight + server handle + ticket 都存在时才打开 WebSocket；否则展示降级说明。
- Interview 页面支持 session snapshot 恢复、turn list、当前问题、submit/recover/cancel/finish command guard。
- Evaluation/Report 页面调用 `GET /interviews/{id}/report` 解析 reportId，使用 evaluationId 提交 append-only feedback，并通过 Evaluation SSE + REST snapshot 恢复。
- Learning 页面实现 list/detail/dashboard 与 plan/item version command；projection stale、不可比和证据不足必须显式。
- Billing 显示 quota/reservation/settlement/release，使用服务端 currencyExponent；Provider cost ledger 只在管理员脱敏投影展示，不与用户权益混算，也不猜真实支付渠道。
- Governance 页面先获取政策/Consent 和 deletion preflight challenge，显示 export/deletion 状态；step-up 或 blocker 未闭合时禁止高风险提交。
- Admin 页面只渲染 catalog/audit/operations 的固定 schema，不枚举未知对象或伪造管理员权限。

建议检查：
- API facade 是否有统一 error envelope 解析。
- Query key 是否包含 tenant/principal/generation。
- 敏感缓存是否可在 logout/privacy deletion 后清理。
- SSE reconnect 是否使用 lastEventId，不重复提交命令。
- WebSocket ticket 是否只留内存，generation/sequence/ACK/NACK/最大 in-flight/chunk/buffer/cancel 是否能进入可恢复 UI；服务端 adapter 无运行证据时保持上传禁用。

不得运行 npm install、npm build、测试、浏览器、服务、Git 或外部调用。

交接：
- 已改文件。
- API 缺口清单。
- 页面状态矩阵。
- 敏感缓存处理说明。
- NotRun 证据状态。
```
