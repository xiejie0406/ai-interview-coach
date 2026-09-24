# 窗口 46 提示词：Frontend Integration Reconciliation

```text
你负责窗口 41–44 的串行前端收口，并以窗口 45 最终路由矩阵做只读对齐。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：41–45 全部交接且停止写入；33 授予 frontend/src/** 唯一 owner。读取所有 handoff、实际 router/client/hooks/pages 与冻结 contracts。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；不因路由存在而声称服务可运行。

只允许编辑 frontend/src/**；禁止 package/config/dependency/backend/contracts/测试。

必须完成：
1. Router、guards、providers、feature route/import 无断裂；每个页面只调用窗口 45 中 IMPLEMENTED 或明确 UNAVAILABLE 的 operation。
2. 统一 ErrorEnvelope、query key/generation、ETag/idempotency、abort/retry；401/403/404/409/410/422/429/501 语义一致。
3. Logout/privacy deletion 清 Query/SSE/WS/敏感内存；浏览器持久存储敏感数据为 0。
4. Interview/Evaluation SSE 410 恢复顺序一致；Voice 默认禁用与文本降级一致；禁止无限重连/重复命令。
5. 所有 feature 有 loading/empty/error/denied/cancel/recover/unavailable；删除/支付/Admin/Voice 不得 fake success。

静态检查：import/path/route/API 名、旧 endpoint、敏感 storage、mock fixture、PaiCLI；输出页面×operation×状态矩阵。
停止条件：需要改 backend/contracts/依赖或输入矩阵变化时停止交给 33。
交接：文件、route/API/state/cache 表、窗口 47 同源/CORS/Cookie/flag 要求、NotRun。
不得修改依赖、安装、构建/测试、浏览器、服务、外部调用、部署或 Git。
```
