# 窗口 34 提示词：Inbound Common / Security Foundation

```text
你负责 AI Interview Coach 入站公共映射和安全壳候选，不实现任何业务 domain endpoint。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

启动门：窗口 32 已交接；窗口 33 已授予 rest/common、inbound/security 与 RestInboundAdapter.java 唯一 owner。先完整读取用户/项目 AGENTS、相关 specs、Feature 候选、implementation-contract-pack、contracts 全部 OpenAPI common/error/security 约定、现有 filters/error handlers 和 application identity/shared context 类型。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；与 PaiCLI 完全独立。

只允许编辑：
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/rest/common/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/security/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/rest/RestInboundAdapter.java

禁止编辑 domain/application/persistence/boot/contracts/frontend/POM/Migration/测试/docs product/technical-architecture/phase-*.md。

必须完成：
1. 统一把 authenticated server session 解析为 QueryContext/OperationContext；不接受客户端 tenantId/userId 覆盖，不把 token 放 URL。
2. Correlation ID、Idempotency-Key、If-Match/ETag、CSRF/Same-Origin、Cookie、content type/size 与 request time 使用一个明确入口；缺字段返回统一 ErrorEnvelope。
3. Domain/Application exception 到 HTTP 的 allowlist 映射；owner-scoped 资源统一 404，不把异常 class/SQL/provider 原文返回客户端。
4. 提供稳定的 CAPABILITY_UNAVAILABLE/501 映射，供业务窗口处理缺 use case；禁止空 200/202 或 mock success。
5. 安全日志只含 correlationId、operationId、status、reasonCode；禁止 idempotency key、session handle、answer/transcript/prompt/audio/object key。
6. 不在本窗口创建 domain controller、SSE emitter、WebSocket handler 或 Boot Bean。

只读检查：application/domain 无反向依赖；common 不 import 业务 internal；现有 error schema 字段逐项一致；敏感 toString/log 为 0；PaiCLI 为 0。

停止条件：需要改 contracts/application/boot、发现现有 session owner 不足、目标目录被占用或安全语义不唯一时停止交给 33。

交接：修改文件；公共 request/error/security 映射表；业务窗口调用方式；窗口 47 所需 Bean；静态结果；NotRun。
不得新增测试、安装依赖、构建/测试/启动、执行 Migration/外部调用/部署/Git。
```
