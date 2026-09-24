# 窗口 41 提示词：Frontend Shared Foundation

```text
你负责前端 app/shared/session 基础候选，不实现具体业务 feature 页面。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：33 授予 frontend/src/app/**、shared/**、main.tsx、styles.css 唯一 owner。完整读取用户/项目 AGENTS/specs、contracts、现有 router/providers/client/session 与 Wave 4 索引。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；后端无运行证据。

只允许编辑：
- frontend/src/app/**
- frontend/src/shared/**
- frontend/src/main.tsx
- frontend/src/styles.css（仅全局基础/token，不重写 feature 视觉）

禁止编辑 frontend/src/features/**、backend/contracts/package.json/Vite/tsconfig/测试/docs product/phase。

必须完成：
1. API client 统一 credentials、CSRF、correlation、Idempotency-Key、If-Match、ErrorEnvelope、abort/timeout；不把 token/tenant/user 当客户端权威。
2. SessionProvider 明确 loading/authenticated/anonymous/expired/unavailable；logout/privacy deletion 可清空 Query cache、SSE/WS generation 与敏感内存。
3. Query key 必须包含服务端 principal/tenant generation；切换/注销不能复用旧缓存。
4. 通用 AsyncState 覆盖 loading/empty/error/401/403/404/409/410/422/429/501/retry/cancel；不把 501 渲染成成功空态。
5. 提供 feature 可复用的 operation key、ETag、recovery generation 和 in-memory-only sensitive handle 边界；localStorage/sessionStorage/IndexedDB 禁止 token、ticket、raw answer/transcript/audio Blob。
6. Router 只维护现有 route shell/guard/error boundary；不猜新增产品路由。

只读检查：shared 不 import feature internal；持久存储敏感键为 0；ErrorEnvelope 字段与 contract 一致；无硬编码用户/tenant/provider/PaiCLI。
停止条件：需要改 contracts/backend/feature、依赖缺失或目录占用时停止交给 33。
交接：文件、API/error/session/cache 矩阵、42–44 使用约定、46 待收口项、NotRun。
不得修改依赖、安装、构建/测试、浏览器、服务、外部调用、部署或 Git。
```
