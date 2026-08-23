# SPEC-backend-bridges：后端为三端前端暴露的统一网关层（港/桥 module，方案 B）

> 文档状态：**Draft / WaitingForApproval**
> 文档类型：架构 Spec（候选，未批准）
> 创建日期：2026-08-16
> 最近更新：2026-08-16（方案 B 重写）
> 出品阶段：R1（多端前端回归路线 / 方案 B）
> Spec ID：SPEC-backend-bridges
> 关联决策：`DEC-027`、`DEC-028`、`DEC-073`
> 关联设计：`docs/architecture/technical-architecture.md` 第 5 节（Container）、第 13 节（API）
> 关联授权：`AGENTS.md` 第 7 节 / 第 30 行 / 第 34 行；`decision-register.md` 第 167 行 + 第 2.4 节

本 SPEC 在以下条件**全部满足**之前**不执行**：

1. 三端 SPEC 至少其一 Approved（admin-web / portal-web / mobile）；
2. 用户对 `DEC-022 / 070 / 071 / 072 / 073` 至少一个 Accepted（已完成）；
3. 单独授权：`backend/` 内允许创建新 module + `pom.xml` 改动；
4. `frontend/` 处置选项（A/B/C）已选定 + 对应授权（间接影响：本 SPEC 提供的端点需要保证 admin-web / portal-web 调用契约与 React 旧版兼容直到切换完成）。

## 1. 一句话与目标

在现有 `backend/` 5 个 Maven module（`interview-domain / -application / -adapters / -boot / -test-support`）**之外**新增 3 个新的 Maven module：`interview-portal-bridge`、`interview-admin-bridge`、`interview-mobile-bridge`。它们的唯一职责是：

> **为三端前端提供统一、清晰、可鉴权、可 SSE / WebSocket 的网关层端点；不修改现有 domain / application / adapters。**

不重写后端业务核心；现有 5 module 维持不变；现有 15 份 Flyway migration 不变；现有 Spring Modulith 边界不破。

## 2. 范围

### 2.1 新增 Maven module

| module | 责任 | 复用现有 |
|---|---|---|
| `interview-portal-bridge` | PC 前台（H5 / Web）专用：登录态续期、报告分享、邮件导出签名 URL | identity, evaluation |
| `interview-admin-bridge` | 后台专用：内容审核/审计只读投影；操作日志查询签名 | catalog, governance |
| `interview-mobile-bridge` | 移动端专用：录音上传/签名 URL、断线续传 resume token、小程序 session 换 Cookie | voice, identity |

### 2.2 每个 module 内层结构

```
interview-<name>-bridge/
├── src/main/java/com/aiinterviewcoach/bridge/<name>/
│   ├── *BridgeApplication.java         // 在 R1 期间留空（含 package-info）
│   ├── config/                         // 仅占位
│   ├── controller/                     // 仅占位
│   ├── dto/                            // 仅占位
│   ├── security/                       // 仅占位：转给 backend/interview-boot 已有 Spring Security
│   └── package-info.java
└── pom.xml                              // 仅占位，不引入任何业务依赖
```

R2 之前：

- 仅保留 `*BridgeApplication.java` 空类与 `package-info.java`
- 不写 Controller / DTO / 业务代码
- `pom.xml` 仅引入后端内部 module；不引入 Provider SDK / 全局工具
- 不向 `backend/pom.xml` 的 `<modules>` 段写入这些 module

R2 之后（独立授权后）：

- 按 controller / dto / security 完整落地
- 在 `backend/pom.xml` 注册
- 全部承接 `interview-application` 的 port；不破坏现有 Modulith 边界

### 2.3 协议分工

| 协议 | 用途 |
|---|---|
| REST `/api/v1/*` | CRUD / 命令 / 查询 |
| SSE `/api/v1/streams/*` | 文本 token、报告进度、用量事件 |
| WebSocket `/ws/v1/interviews/{id}/voice` | 音频双向流 |
| Webhook `/api/v1/webhooks/*` | 支付 / Provider 回调 |

不引入 GraphQL / gRPC；不另起一进程。

### 2.4 与 frontend/ 切换的契约兼容

admin-web 与 portal-web 在 R2 期间**同时支持**：

- 旧路径：与现有 React 19 `frontend/` 的 fetch 调用兼容（保留 `frontend/src/shared/api/client.ts` 现有调用路径）；
- 新路径：admin-web / portal-web 的 Pinia store 调用（路径保持 `/api/v1/*` 不变，仅客户端实例变化）。

bridge module 在切换期必须**双兼容**——同一组端点既能服务旧 React 也能服务新 Vue 3。R3 后旧 React 端点可下线，但必须先经过 admin-web / portal-web 灰度上线 + 旧路径 0 流量验证。

## 3. 非目标

- 不取代 `interview-adapters` 的 Provider 适配（仍是 Adapter 模块责任）
- 不引入新的 Flyway migration
- 不动 `interview-domain` 的纯领域模型
- 不破坏 Spring Modulith 的 `@ApplicationModuleListener` 链路
- 不新增数据库 / Redis / 对象存储依赖

## 4. 鉴权与 CORS

- 沿用 DEC-028 A：同源 Secure HttpOnly Cookie Session；**不**接受 `Authorization` Bearer Token
- CORS allowlist 仅由 `backend/interview-boot/SecurityConfiguration` 精确控制；bridge module 不重新声明 CORS bean
- CSRF：保留 Spring Security 默认同源策略
- 移动端 Cookie Session 续期：`interview-mobile-bridge` 提供 `POST /api/v1/mobile/session/refresh-from-mini-program` 端点占位，由 R5 SPEC-移动端 接口契约补充

## 5. SSE 与 WebSocket

- SSE：bridge module 内仅暴露 façade Controller；真实事件流经 `interview-application` 的 use case 与 `interview-adapters` 的 SSERepository
- WebSocket：`interview-mobile-bridge` 与 `interview-portal-bridge` 不直接打开 `ServerEndpoint`；复用 `interview-boot` 已有 WebSocket 配置
- 不在 bridge module 内实现"新增协议"

## 6. 测试（R2 阶段）

- JUnit 5 + MockMvc
- 关键路径：登录 / 权限 / 错误码 / SSE eventId 去重 / WebSocket sequence
- 不引入 Testcontainers 之外的重量级测试依赖

## 7. 安全红线（R2 落地后强制）

- ❌ 不在 controller 内打印请求体 / 响应体
- ❌ 不在 controller 内拼接 SQL
- ❌ 不在 controller 内直接持有 Provider Key
- ❌ 不在 dto 内塞 `Map<String, Object>` 当入口/出口
- ✅ 每个端点有一个 `*BridgeErrorCode` 枚举与统一 `ErrorResponse`
- ✅ `@PreAuthorize` 必须在 controller 上或 service 上
- ✅ 移动端上传音频走签名 URL；bridge controller 收到后仅做"接收 + 校验"两类操作

## 8. 不可执行动作（R1 期间）

按 R0 评审报告第 9 节；同时：

- ❌ 不创建 `interview-portal-bridge / interview-admin-bridge / interview-mobile-bridge` 任何目录
- ❌ 不修改 `backend/pom.xml` 的 `<modules>` 段
- ❌ 不引入 `spring-boot-starter-graphql` 等新 starter
- ❌ 不引入 Spring Modulith 不允许的跨 module 调用

## 9. 验收（进入 R2 实现合约的 Gate）

- [ ] 三端 SPEC 至少其一 Approved
- [ ] 用户对 `DEC-022 / 070 / 071 / 072 / 073` 至少一个 Accepted（已完成）
- [ ] 单独授权：`backend/` 内允许创建新 module + `pom.xml` 改动
- [ ] `frontend/` 处置选项（A/B/C）已选定 + 对应授权

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| bridge module 沦为转交点腐化 | 与 `interview-adapters` 重复 | 评审逐 controller 检查"是否仅做透传"；不写业务 |
| Cookie Session 在三端不同源失效 | 用户被登出 | bridge 与后端全部同源部署；如有跨域需求走 allowlist 单域 |
| 移动端签名 URL 变成直连 Provider 的捷径 | 破 DEC-028 / DEC-048 | 第 7 节红线 + R5 评审 |
| WebSocket sequence 不一致 | mobile / PC resume 错位 | 桥接层不重新定义 `sequence` 字段；遵循 `technical-architecture.md` § 13.4 |
| 切换期新旧路径不双兼容 | React 旧版与 Vue 3 同时崩溃 | SPEC 第 2.4 节硬约束；R3 灰度上线 + 旧路径 0 流量验证 |