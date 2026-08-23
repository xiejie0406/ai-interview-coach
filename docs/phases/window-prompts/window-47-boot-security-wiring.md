# 窗口 47 提示词：Boot / Session Security / Bean Wiring

```text
你负责 Composition Root 候选，把已存在且安全前置完整的 use case/adapter 显式装配；缺 owner 时保持 unavailable。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：45/46 已交接，所有 inbound/frontend writer 停止；33 授予 backend/interview-boot/src/main/** 唯一 owner。完整读取用户/项目 AGENTS/specs、现有 Boot properties/config、Bean 构造依赖矩阵、V001–V010 与部署/配置 Draft。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；不得启动 Spring Context。

只允许编辑：
- backend/interview-boot/src/main/java/**
- backend/interview-boot/src/main/resources/application*.yml（仅已有安全配置键/默认关闭；禁止 Secret）

禁止编辑 POM、Migration、domain/application/adapters/frontend/contracts/测试/docs product/phase。

必须完成：
1. Session→ResolvePrincipal→tenant/owner、CSRF/Same-Origin、Admin guard、REST/SSE/WS filter chain 明确；真实身份渠道未配置时 fail-closed。
2. 只装配构造依赖完整的 use case。缺 Policy/Provider/Source/Dashboard/ConsentPolicy/Privacy/Admin/Payment/Voice/LiveSSE port 时对应 capability unavailable，不提供成功 Mock。
3. `DomainEventEnvelopePolicy` 与 durable event policy 必须显式 allowlist；retention/heartbeat/replay limit 由校验后的配置传入，不硬编码待决值。
4. `SensitiveEnvelopeCipher` 没有真实受控 key owner 时 Persistence capability fail-closed；禁止 Base64/固定 key/明文降级。
5. Flyway V001–V010 保持仅配置、未执行；Redis/Object storage/Provider/ASR/TTS/Payment 默认关闭。
6. 避免可选 Bean 让整个 Context 假装成功：每个 unavailable route 有稳定 reasonCode，启用条件可审计。

静态检查：Bean 构造参数、重复/循环依赖、properties validation、default-off、secret、PaiCLI；输出 operation→Bean→missing owner 表。
停止条件：需要新增依赖/POM、猜供应商版本/Secret/TTL、需运行 Context 或改 Migration 时停止交给 33。
交接：文件、Bean/条件装配矩阵、默认关闭项、仍阻断启动能力、NotRun。
不得安装依赖、构建/测试/启动、Migration、外部调用、部署或 Git。
```
