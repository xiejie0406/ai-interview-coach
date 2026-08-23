# 窗口 22 提示词：Persistence / Migrations

```text
你负责 AI Interview Coach 的持久化与 migration 候选实现。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

必须先完整读取用户级 AGENTS/spec、项目 AGENTS、docs/specs/README.md、implementation-contract-pack.md、code-landing-readiness-review.md，并确认当前仍是 L3 / 阶段 3 WaitingForApproval / NotRun。

只允许编辑：
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/**
- backend/interview-boot/src/main/resources/db/migration/V00*.sql（仅使用当前未冲突、未执行且由本窗口登记的版本号）

禁止修改：
- domain、application、contracts、frontend、POM、测试代码、docs/product、docs/architecture/technical-architecture.md、docs/phases/phase-*.md。

目标：
- 沿用项目现有 `NamedParameterJdbcTemplate`/Spring JDBC 风格，为 Identity、Governance、Catalog、Practice、Billing、Interview、Evaluation、Learning、Platform、Voice 建立保守 JDBC/SQL 候选持久化；不得并行引入 JPA 或第二套持久化技术。
- 所有业务表必须有 tenant_id、owner_user_id 或明确 owner 说明、version、created_at、updated_at。
- 强制 tenant 组合唯一约束，避免跨 tenant 读取和引用。
- 建立 outbox、job、idempotency、entitlement reservation、usage ledger、cost ledger、audio artifact、transcript、privacy deletion、audit log 的表和索引。
- JSONB 只用于 snapshot/config/output，不用于逃避核心 owner、tenant、status、version、外键和唯一约束。
- 敏感正文列必须命名清楚，默认按 encrypted_* 或 redacted_* 设计；不要伪装已经加密。
- Migration 只定义结构，不插入真实业务数据。
- 所有 migration 仍是未执行 Draft；交接必须列出版本号、依赖和是否允许窗口 30 修正，不能声称已经应用。

建议文件：
- adapters/outbound/persistence/shared/*：tenant/owner/version、JSON/加密信封与乐观锁公共映射。
- adapters/outbound/persistence/identity/*RepositoryAdapter。
- adapters/outbound/persistence/catalog/*RepositoryAdapter。
- adapters/outbound/persistence/practice/*RepositoryAdapter。
- adapters/outbound/persistence/interview/*RepositoryAdapter。
- adapters/outbound/persistence/evaluation/*RepositoryAdapter。
- adapters/outbound/persistence/learning/*RepositoryAdapter。
- adapters/outbound/persistence/billing/*RepositoryAdapter。
- adapters/outbound/persistence/governance/*RepositoryAdapter。
- adapters/outbound/persistence/voice/*RepositoryAdapter。
- 按 schema owner/依赖顺序拆分的 V00* migration；开始前先枚举现有版本，禁止重复编号。

注意：
- 如果 domain/application 端口尚不存在，只能创建 adapter 内候选实体和 mapper 注释，不要跨包新增端口。
- 不得把缺少端口的问题用反向依赖解决。
- 不得引入 PaiCLI 或外部数据库连接。

只读静态检查：
- package/path 一致。
- adapter 不被 domain/application 反向引用。
- migration 中 tenant/version/status/index/unique/foreign key 覆盖核心表。
- 无真实 secret、无 PaiCLI 引用。

不得运行构建、测试、迁移、服务、Git 或外部调用。

交接：
- 已改文件。
- 表清单与 owner。
- 仍缺的 repository port。
- 无法映射的契约裂缝。
- 证据状态 NotRun。
```
