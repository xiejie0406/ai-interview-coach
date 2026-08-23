# 窗口 29 提示词：Boot / Session Security / Bean Wiring

```text
你负责 AI Interview Coach 的 Boot 装配、Session 安全过滤链和 use-case Bean 候选接线。目标是让已存在的业务入口具备可装配边界，同时保持外部能力默认禁用和安全失败；不要启动服务或声称 endpoint 可运行。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

启动条件：
- 窗口 22、23、24、27、28 均已交接。
- 先核对这些窗口的新增端口与 Adapter，不得猜造不存在的实现。
- 若窗口 24 仍在编辑 inbound/security，立即停止，待其交接后再开始。

事实边界：
- 项目与 PaiCLI 完全独立。
- 风险为 L3；主阶段仍是阶段 3 WaitingForApproval；运行证据全部是 NotRun。
- 不锁定未决 Provider、ASR/TTS、对象存储、支付、账号找回渠道或版本。

只允许编辑：
- backend/interview-boot/src/main/java/com/aiinterviewcoach/boot/**
- backend/interview-boot/src/main/resources/application.yaml
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/security/**

禁止修改：
- domain、application、其他 adapter、contracts、frontend、POM、migration、测试代码、docs/product、docs/architecture/technical-architecture.md、docs/phases/phase-*.md。

必须完成 A：Session 与请求安全
1. `AIC_SESSION` 只能是 HttpOnly/Secure/SameSite 约束下的 opaque session handle；Filter 通过 Identity application port 解析 session，不解析自包含权限，不把 token 放进 local/session storage。
2. tenantId 与 userId 必须来自服务端权威 Session/Principal；禁止从可伪造请求头、path 或 body 直接信任 tenant。
3. 写请求执行 CSRF 与 same-origin 校验；登录、刷新、登出、注册、隐私删除、管理员操作分别遵循契约要求。失败映射为统一 ErrorEnvelope，不返回框架默认 HTML。
4. 未认证返回 401；已认证但权限不足返回 403；owner-scoped 资源越权按契约返回不泄漏存在性的 404/稳定错误码。
5. 管理员权限通过 application permission decision port；禁止根据前端路由、email、静态用户名或请求字段判定管理员。
6. correlation/request id 可生成或接收受约束值，但日志不能包含 cookie、CSRF token、socketTicket、回答、Transcript、Prompt 或支付正文。

必须完成 B：Bean 装配与条件能力
1. 为当前真实存在的 use case/internal service 显式定义 Bean；构造参数缺失时保持 fail-fast 或 endpoint unavailable，禁止空实现/成功 mock。
   必须特别核对窗口 28/边界复核新增的 `InterviewRecoveryProjectionPort`、`DefaultConfirmedTranscriptAnswerPort`、`InterviewVoiceAccessPort`、`ContentDigestPort`、`RegistrationConsentPort`、`InterviewPlanPolicyPort`，以及 Interview 现在依赖的 `EntitlementPort`；不得重新把 `BillingRepository` 注入 Interview 用例，也不得让 Identity 注册直接注入 `ConsentRepository`。缺少 plan policy 实现时 Create Interview Plan 必须 unavailable，不能在 Boot 猜题数、预算、expiry 或 profileVersion。
2. SecurityFilterChain 允许健康检查、静态资源及契约明确的匿名 identity 操作；已装配的业务 endpoint 按 session/CSRF/role 保护，不能继续无差别 deny-all，也不能 permit-all。
3. Disabled Provider/ASR/TTS adapter 是默认值；只有配置完整、明确启用且对应 adapter 存在时才切换。不得在 application.yaml 写真实 secret、供应商模型名或猜定版本。
4. Job Worker、Outbox Publisher、Flyway、Redis、对象存储和外部 Provider 使用独立条件开关；缺少持久化/lease/transaction 能力时 Worker 不启动。
5. Boot 只能做 composition root，不承载业务状态机、tenant 判定、幂等规则、计费规则或 Provider fallback 决策。
6. 避免 Bean 循环依赖；跨子域协作通过 application port/bridge，Adapter 不直接互调 Controller。

只读静态检查：
- 每个 Controller 依赖的 use case 有且仅有一个明确候选 Bean，或被显式 unavailable 条件保护。
- Session/tenant/principal/CSRF/admin 决策链可从 Filter 追踪到 application port。
- 默认配置下真实 Provider、ASR/TTS、对象存储、支付和 Worker 均不会被误启用。
- domain/application 未反向依赖 Spring、adapter 或 boot。
- 无真实 secret、无 PaiCLI 引用、无敏感日志。

不得运行 Maven/npm 构建、测试、服务、迁移、Provider、Git 或外部调用。

交接必须列出：
- 修改文件与 Bean 装配矩阵。
- 匿名/认证/管理员路由安全矩阵。
- 默认禁用和 fail-fast/unavailable 条件。
- 尚未能装配的端口与原因。
- 未执行项与证据状态 NotRun。
```
