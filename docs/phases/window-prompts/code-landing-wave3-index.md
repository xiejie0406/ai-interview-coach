# Wave 3 代码落地多窗口提示词索引

> 文档类型：多窗口提示词索引 / Draft  
> 文档状态：Draft  
> 风险等级：L3  
> 当前主阶段：阶段 3 功能规格，WaitingForApproval  
> 证据结果：NotRun  
> 更新时间：2026-08-03  

## 使用顺序

先打开只协调、不写生产代码的窗口：

- [窗口 20：Wave 3 协调与冲突控制](window-20-wave3-coordinator.md)

第一批建议并行：

1. [窗口 21：Evaluation / Report / Learning](window-21-evaluation-report-learning.md)
2. [窗口 22：Persistence / Migrations](window-22-persistence-migrations.md)
3. [窗口 23：Core Application Services](window-23-core-application-services.md)

第一批交叉审查后的必要修正：

4. [窗口 31：Core Application 边界与幂等收口](window-31-core-application-boundary-reconciliation.md)
5. [窗口 27：Evaluation / Report / Learning 契约收口](window-27-evaluation-learning-contract-reconciliation.md)

第一批交接后的业务边界收口：

6. [窗口 28：Interview Snapshot / Voice Answer 原子收口](window-28-interview-snapshot-voice-commit.md)

持久化契约回填：

7. [窗口 30：Persistence 契约回填](window-30-persistence-contract-reconciliation.md)

SSE durable replay 前置收口：

8. [窗口 32：Durable SSE Stream / Replay](window-32-durable-sse-replay.md)

第二批在窗口 22、23、27、28、30、31、32 交接后启动：

9. [窗口 24：REST / SSE / WebSocket Adapters](window-24-rest-sse-websocket-adapters.md)
10. [窗口 25：Frontend Contract Alignment](window-25-frontend-contract-alignment.md)

第二批交接后的 Composition Root 收口：

11. [窗口 29：Boot / Session Security / Bean Wiring](window-29-boot-session-security-wiring.md)

最后启动：

12. [窗口 26：Integration Static Review](window-26-integration-static-review.md)

## 统一强规则

- 项目目录固定为 `D:\2025Ai\26-05-23\ai-interview-coach`。
- AI Interview Coach 与 PaiCLI 完全独立，禁止依赖 PaiCLI 源码、Jar、Maven module、Runtime API、数据库、配置、前端、部署或 Git 历史。
- 当前风险等级为 L3；主阶段仍为阶段 3 `WaitingForApproval`；所有运行证据仍为 `NotRun`。
- 当前提示词是 Draft 候选执行提示，不是 Approved tasks.md，不得把任何产物描述为已批准、已验收或已上线。
- 不修改被截断的 `docs/product/prd.md` 和 `docs/architecture/technical-architecture.md` 原文。
- 不修改既有 `docs/phases/phase-*.md`。
- 未获单独授权前，不新增测试代码、不安装依赖、不运行 Maven/npm 构建或测试、不启动服务、不执行迁移、不调用真实 Provider/ASR/TTS/支付/对象存储、不做部署、不执行 Git add/commit/push/PR。
- 每个窗口只改自己提示词列出的允许范围；需要跨范围时停止并交给协调窗口。
- 窗口 28 与 23 会触及相同 application 子目录，必须串行；窗口 29 与 24 会触及相同 security 目录，也必须串行。
- 窗口 30 必须等待 22、27、28、31，负责消除并行期间 Core/业务旧模型进入 Entity/SQL 的映射裂缝。
- 窗口 32 必须等待 30，关闭真实 stream_event/Last-Event-ID replay 缺口后，窗口 24 才能实现 durable SSE。
- 窗口 31 必须等待 23，先修复跨域 internal import、幂等四态和匿名注册 scope，再允许 27/28/24 依赖 Core Application。

## 交接格式

每个窗口结束时必须交接：

- 实际修改文件清单。
- 只读静态检查结果。
- 未执行项与证据状态 `NotRun`。
- 新发现的公共契约裂缝。
- 需要其他窗口承接的文件或接口。
- 是否发现 PaiCLI 引用、敏感日志、反向依赖、owner/tenant/version 破坏。
