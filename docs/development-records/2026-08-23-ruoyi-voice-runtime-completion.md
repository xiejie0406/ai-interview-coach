# RuoYi 语音运行链路剩余收口记录

> 文档类型：开发记录
> 文档状态：Draft
> Feature / 任务 ID：FEAT-INTERVIEW-001 / TASK-RUOYI-VOICE-03
> 风险等级：L3
> 当前阶段：8. 审查验证
> 阶段状态：InProgress（A 包实现完成；协议 handler 测试证据未完成）
> 证据结果：Blocked（Testcontainers PostgreSQL 依赖不可用；WS/TTS/SSE handler 确定性测试未执行）
> 更新时间：2026-08-23
> 前置：[`../phases/window-prompts/window-50-voice-runtime-remaining-closure.md`](../phases/window-prompts/window-50-voice-runtime-remaining-closure.md)

## 1. 执行边界与输入基线

- 本轮重新以当前工作区源码和 Window 49 实际差异为准复核；未覆盖、恢复或静默改写用户修改。
- 已确认项目根不存在项目级 `AGENTS.md`，也未执行任何 Git 命令。
- 用户仅批准 A 包：本地源码/配置/测试/文档修改、Maven 测试与打包、三个前端构建，以及首次失败后一次范围内修复重跑。
- B/C 包、UAT、上线准备、发布、回滚和 Git 均未授权且未执行。
- Window 49 的历史 Pass 不作为本轮最终证据；本记录只引用本轮命令和当前文件。

## 2. 本轮实际修改

### 后端与数据库

- `apps/platform-backend/ruoyi-interview/pom.xml`：补充 Testcontainers PostgreSQL 测试所需的 test-scope 依赖。
- `apps/platform-backend/ruoyi-interview/src/main/resources/db/migration/V6__interview_recovery_dependency_facts.sql`：增加恢复投影可达的最小业务事实表，保持 `ruoyi_user_id`、租户隔离、不可变版本、幂等和 stream 约束，不创建 `identity.*` 外键。
- `apps/platform-backend/ruoyi-interview/src/test/java/com/ruoyi/interview/migration/PostgresVoiceMigrationTest.java`：从空 PostgreSQL 执行最新 Flyway 并验证 durable stream repository 的租户/cursor round-trip（受 Docker 环境门控）。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/controller/InterviewStreamController.java`：有界 replay/live 等待、heartbeat、断开清理、最大连接时长和 SSE payload allowlist。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/controller/websocket/VoiceWebSocketHandler.java`：chunk/总字节/时长限制、背压状态、重复/缺口处理、TTS 取消与断线降级。

### 客户端

- `frontend/src/features/interview/pages/InterviewPages.tsx`：主 React 接入既有 `useVoiceSocket`，串联流式分片、ASR final、转写确认、TTS 播放/取消和 snapshot/资源清理。
- `frontend/src/features/voice/input/Recorder.tsx`：支持流式录音分片，避免完整音频落盘。
- `apps/portal-web/src/features/interview/InterviewRoomPage.vue`：补齐 ACK/flow-control、TTS chunk 终态、播放拒绝和关闭清理。
- `apps/mobile/pages/interview/room.vue`：补齐 H5 ACK/flow-control、音频限制、TTS 终态和资源释放；非 H5 继续文字降级。

### 本次启动修复（用户追加授权）

- `VoiceWebSocketConfiguration` 改用 `ObjectProvider` 延迟获取 WebSocket handler/interceptor，消除配置类自注入循环。
- 移除语音 REST Controller 的 `final` 修饰，允许 RuoYi 权限 AOP 生成 CGLIB 代理。
- 将 Interview `PersistenceJsonCodec` Bean 标记为 `@Primary`，消除组件 Bean 与配置 Bean 的歧义。
- 为 `ServiceMetadata` 提供基于 `ruoyi.name/version` 的配置 Bean。
- `PlanQuestionSelectionPort`、`CreateInterviewPlan`、`ProgressInterview` 在业务 REST safety gate 关闭时不装配，避免空 `INTERVIEW_CATALOG_PUBLIC_TENANT_ID` 阻断启动。

## 3. 契约与代码矩阵（当前实现）

| 链路 | 当前实现 | 本轮证据 |
|---|---|---|
| RuoYi principal/RBAC -> voice session/ticket | 复用既有 RuoYi JWT/SecurityContext，一次性主体绑定 ticket | 静态源码审计 Pass；真实登录/403 NotRun |
| WS start/chunk/stop/cancel | `VoiceWebSocketHandler` 限制 sequence、generation、codec、chunk/window、总字节/时长、ACK/NACK、背压和取消 | Maven 编译 Pass；handler 确定性测试与联机 WS NotRun |
| ASR final -> transcript confirm | 主 React/Portal/Mobile H5 使用同一消息语义，非 H5 保持文字降级 | 三端构建 Pass；真实 ASR NotRun |
| TTS STARTED/chunk*/COMPLETED、CANCELLED、FAILED/degraded | 客户端内存播放、取消、断线和 Provider 未配置终态均释放资源并降级 | 三端构建 Pass；TTS deterministic fake/真实 Provider NotRun |
| PostgreSQL Artifact/Transcript/execution/stream | V6 补齐恢复投影依赖；Jdbc stream 保留 owner/tenant/cursor 约束 | 静态 migration contract Pass；Testcontainers Blocked |
| durable stream -> authenticated SSE replay/heartbeat/410 | Controller 先 replay，再有界等待新事件，heartbeat、最大时长、断开清理和 allowlist | Maven 编译/单测 Pass；SSE 联机 NotRun |

## 4.1 配置、reasonCode 与默认关闭清单

- `interview.foundation-safety.business-rest-endpoints-enabled`：默认 `false`；未获 B 包前不开放业务 REST。
- `interview.foundation-safety.external-provider-calls-enabled`：默认 `false`；ASR/TTS/Chat 的真实 HTTP adapter 不装配。
- `interview.foundation-safety.object-storage-writes-enabled`：默认 `false`；未配置 `interview.voice-runtime.local-storage-root` 时 fail-closed。
- `interview.voice-runtime.sensitive-envelope-key-id` / `...key-base64`：从外部环境注入，缺失时使用不可用 cipher，不记录 key 内容。
- 已保留并统一使用的稳定 reasonCode：`VOICE_NOT_READY`、`OBJECT_STORAGE_NOT_READY`、`ASR_NOT_READY`、`ASR_NOT_CONFIGURED`、`TTS_NOT_CONFIGURED`、`VOICE_BACKPRESSURE`、`TTS_SOCKET_NOT_AVAILABLE`、`TTS_PIPELINE_FAILED`、`TTS_CANCELLED`、`TTS_SIZE_MISMATCH`。
- 非 H5 客户端没有满足已批准的 `uni.connectSocket`/分片/子协议认证条件，继续文字降级，不把 H5 构建证据外推到原生端。

## 4. 获授权命令与证据

| EV | 工作目录 | 命令 | 退出码/结果 | 事实边界 |
|---|---|---|---|---|
| EV-50-01 | `apps/platform-backend` | `mvn -pl ruoyi-interview -am test`（首次） | 非 0，Fail | Testcontainers 启动因本机 Docker 不可用失败；未执行真实 PostgreSQL migration |
| EV-50-02 | `apps/platform-backend` | 同上（一次范围内修复后重跑） | 0，Pass | 唯一范围内修复是将 Testcontainers 启动失败转换为 JUnit `Assumptions.abort`，避免环境缺失伪装成业务断言失败；其余 9 个可执行测试通过。`PostgresVoiceMigrationTest` 报告 `Tests run: 0`，因此 PG 结果仍为 Blocked |
| EV-50-03 | `apps/platform-backend` | `mvn -pl ruoyi-admin -am -DskipTests package` | 0，Pass | `ruoyi-interview` reactor 与 `ruoyi-admin` repackaged jar 成功；tests skipped |
| EV-50-04 | `frontend` | `npm run build` | 0，Pass | 主 React 构建通过 |
| EV-50-05 | `apps/portal-web` | `npm run build` | 0，Pass | `vue-tsc` 与 Vite 构建通过；既有 chunk warning 不影响退出码 |
| EV-50-06 | `apps/mobile` | `npm run build:h5` | 0，Pass | uni H5 `DONE Build complete`；不代表小程序/原生联机通过 |
| EV-50-07 | `apps/admin-web` | 未执行 | NotApplicable | 本轮未修改 Admin 代码或共享契约，按 A 包条件不运行 `build:prod` |
| EV-50-08 | 项目根及允许源码目录 | `rg` 敏感日志/凭据静态扫描 | 0，Pass（无泄露值） | 仅命中已批准的字段名、协议注释和 `Bearer` 拼接；未发现实际 Token、Cookie、Secret、密码、socket ticket、原始音频、完整转写/回答写入日志或源码 |
| EV-50-09 | 项目根 | `rg`/PowerShell 允许范围与根 `AGENTS.md` 只读审计 | 0，Pass | 根无项目级 `AGENTS.md`；修改集中在 A 包允许目录；未执行 Git |
| EV-50-10 | `apps/platform-backend` | 临时注入 `RUOYI_DB_PASSWORD`/`INTERVIEW_DB_PASSWORD` 后执行 `java -jar ruoyi-admin/target/ruoyi-admin.jar` | 进程保持运行，Pass（启动） | MySQL 连接成功，RuoYi 日志出现 `Started RuoYiApplication`，Tomcat `8081` 监听；密码未写入文件或日志。未执行浏览器/UAT、REST/WS/SSE 场景验证 |

## 5. 未执行、Blocked 与停止边界

- PostgreSQL Flyway 实际执行、Repository round-trip、租户/owner 隔离和 cursor/410 运行证据：`Blocked`，原因是 Testcontainers 需要的 Docker 不可用；不能用静态 SQL 断言替代。
- 当前测试目录没有 `VoiceWebSocketHandler` 或 `InterviewStreamController` 的直接测试，也没有 TTS deterministic fake 的成功/失败/取消测试；已有 9 个测试覆盖领域状态、ticket、storage、cipher、cursor、migration contract 和 capability fail-closed，不能外推为 WS/TTS/SSE 运行证据。该补测在本轮授权的一次失败修复重跑额度之外，记为 `NotRun`。
- RuoYi 服务启动、REST/WS/SSE 联机、401/403、浏览器验收：`NotRun`，属于 B 包或 UAT。
- MySQL 权限脚本、真实 ASR/TTS、云对象存储、真实音频上传/回读/删除：`NotRun`，分别属于 B/C 包。
- 未写真实业务数据库或权限表、未调用 Provider/云服务、未执行浏览器验收、未安装系统软件、未执行 Git。
- 本轮用户追加授权后已启动唯一 RuoYi 进程；仍未执行权限脚本、Flyway migration、业务 REST/WS/SSE 联机场景或浏览器验收。
- 未触发 Window 50 的公共 API/event/schema/权限意图变更、第二套认证入口、禁止目录修改、跨租户访问、Secret/Token/原始音频泄露等停止条件。

## 6. 当前结论与下一阶段门

A 包的源码、配置和客户端实现，以及获授权的 Maven/三端构建已完成；但因 Docker 缺失和 WS/TTS/SSE handler 确定性测试未执行，本记录不能声明 A 包完成定义中的完整自动化证据，也不能升级为完整本地运行验证或 UAT 通过。

下一阶段为 B 包候选门：需用户单独批准精确的 PostgreSQL/权限脚本/唯一 RuoYi 启动和 REST/WS/SSE 联机命令、端口、测试数据、清理与恢复方式；在该批准前保持当前工作区修改，不执行外部写入。发布、回滚和 Git 仍需独立授权。

恢复方式：保留旧 `backend/` 与历史迁移；本轮新增/修改文件均在第 2 节列明，可由后续经批准的变更按文件级审查恢复。当前无 Git 回滚点。
