# AI Interview Coach Contracts

本目录保存浏览器、后端和外部适配器共享的版本化协议。当前契约处于实现候选基线；文件存在不代表 endpoint 已实现或验证通过。

- `openapi/`：REST 命令、查询、错误、认证、幂等和并发语义。
- `asyncapi/`：SSE、WebSocket 与 Webhook 的事件、排序、续传和终态。
- `schemas/`：Agent 与公共信封的 JSON Schema；模型输出必须先通过 schema 和确定性门禁。

通用规则：

1. 浏览器使用同源 Secure/HttpOnly Cookie；unsafe method 另需 CSRF token。
2. 创建、命令、高成本和外部副作用请求必须使用 `Idempotency-Key`。
3. mutable aggregate 使用 `If-Match`/`expectedVersion`；服务端仍是状态和权限真相。
4. SSE 只下行文本/状态；WebSocket 只承载语音 chunk 与语音控制；Webhook 只供服务端回调。
5. Error/Event/Agent schema 的字段或枚举变化必须版本化；consumer 不得私建副本。
6. 所有当前验证结果均为 `NotRun`。

当前协调基线还要求：

- 公共错误字段统一为 `error.userMessage`；内部异常文本和 Provider 原始错误不得透传。
- Report 必须通过明确的 `interviewId -> reportId` 查询解析；Learning plan 必须有列表/详情恢复接口。
- 金额同时携带 `amountMinor + currency + currencyExponent`，客户端不得猜小数位。
- 删除先取得服务端 challenge；step-up、blocker、隐藏、物理删除和保留窗口是不同事实。
- Voice ticket 只在首个 `client.hello` 帧发送；generation、双向 sequence、ACK/NACK 和服务端 flow limit 是上传前置条件。

这些仍是 Draft 候选契约。Adapter、前端类型或文件存在均不能证明 endpoint 已实现；真实契约检查、构建和 UAT 仍为 `NotRun`。
