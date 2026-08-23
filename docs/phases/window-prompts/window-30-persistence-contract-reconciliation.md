# 窗口 30 提示词：Persistence 契约回填

```text
你负责把窗口 22 的 Persistence/Migration 候选，与窗口 31、27、28 收口后的 Core、Evaluation、Learning、Interview、Voice 权威模型重新对齐。这个窗口是串行修复，不得和窗口 22、31、27、28 同时写文件；本窗口启动后禁止重跑窗口 22。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

启动条件：
- 窗口 22 已交接全部表、Entity、Repository Adapter 和 migration 清单。
- 窗口 31 已交接 Identity/Catalog/Practice/Interview/Billing/Governance/Platform 的公共 port 与 persistence 缺口。
- 窗口 27 已交接 Evaluation/Report/Learning 最终候选字段。
- 窗口 28 已交接 InterviewSnapshot 与 ConfirmTranscript 原子事务端口。
- 先读取四个窗口的交接与当前实际文件；不得按旧提示词或记忆映射。

事实边界：
- 项目与 PaiCLI 完全独立。
- 风险为 L3；主阶段仍是阶段 3 WaitingForApproval；运行证据全部是 NotRun。
- migration 从未执行，可以在协调记录明确后修正 Draft；不得假装已部署或已回滚。

只允许编辑：
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/evaluation/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/learning/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/identity/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/catalog/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/practice/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/interview/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/voice/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/billing/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/governance/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/platform/**
- backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/outbound/persistence/shared/**（仅被上述 owner 共用的 mapping/cipher 边界）
- backend/interview-boot/src/main/resources/db/migration/V00*.sql

禁止修改：
- domain、application、inbound adapter、boot Java、contracts、frontend、POM、测试代码、docs/product、docs/architecture/technical-architecture.md、docs/phases/phase-*.md。

必须对齐：
1. Evaluation root 同时持久化 state 与 stage；state 只允许 PENDING/RUNNING/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL/CANCELLED，stage 只允许 QUEUED/EVIDENCE_EXTRACTING/RUBRIC_JUDGING/REPORT_COMPOSING/MANUAL_REVIEW/COMPLETE。
2. Evaluation 输入保存 answer_version_id、answer_hash、source_interview_id、固定 config/prompt/schema/provider-route/rubric 版本；不保存回答或 Transcript 原文。
3. Evidence 保存 answerVersionId、answerHash、type、start/end offset、quoteHash、reasonCode；禁止遗留 contentRef/confidence/dimensionKey 旧权威列。
4. Rubric 保存 categorical judgement、confidence、insufficientEvidence、reasonCodes、evidenceIds 和 limitations；禁止遗留 totalScore/maxScore 数值权威模型。
5. Report root 必须有 evaluation_id 与 source_interview_id 的 tenant-scoped 唯一/外键约束；ReportVersion 不可变，持久化 sections/actions/limitations，正文列按 encrypted_* 或受控 content 引用命名，不得伪装已经加密。
6. Feedback 以 evaluation_id 解析 Report，append-only；comment 正文与 type 分离，不覆盖 EvaluationVersion/ReportVersion。
7. LearningPlan 状态只允许 CANDIDATE/CONFIRMED/COMPLETED/CANCELLED；LearningItem 状态只允许 PENDING/IN_PROGRESS/COMPLETED/SKIPPED/CANCELLED，并保存 item version、question_version_id、reason_codes、scheduled_at。
8. LearningPlan 固定 source_report_id、source_report_version_id、Prompt/Schema/Provider route/config version；历史计划不随激活配置变化而重算。
   Prompt/Schema 与 Provider route 的生产类型以最新 `domain.platform.PromptSchemaPin`、`domain.platform.ProviderPolicySnapshot` 为准；`domain.evaluation` 下同名 deprecated 类型不得重新进入生产映射。
9. Interview persistence 能映射 planVersion/hash、多个 pending Job、reservation/report/voice 最小引用、stream cursor、recovery/failure；不得用一个 pending_job_id 缩减列表。必须为最新 `InterviewRecoveryProjectionPort` 提供 tenant + owner scoped 实现或明确缺口，不能在 application/REST 中临时拼 SQL。
10. ConfirmTranscript 所需 Transcript、AnswerVersion、Session、Job、Outbox、Idempotency 必须能在同一本地数据库事务内保存；组合 FK 必须包含 tenant_id。
    `ConfirmedTranscriptAnswerPort` 已由 Interview application 提供本地事务桥，Persistence 不得再创建第二套 Transcript-only 提交路径。
11. Audio Artifact 覆盖 CREATED/UPLOADING/UPLOADED/TRANSCRIBING/TRANSCRIBED/SYNTHESIZING/SYNTHESIZED/DELETE_QUEUED/DELETED/UPLOAD_FAILED/TRANSCRIBE_FAILED/SYNTHESIS_FAILED/DELETE_PARTIAL，并保留 purpose、delete status、expiry、failure code。
12. Job/Outbox/Idempotency 的 repository port 映射必须与最新 application 接口一致；claim/lease/unique operation 依赖数据库原子约束，不能用先查后写模拟。
13. 对窗口 31 的 Identity/Catalog/Practice/Billing/Governance port 逐项给出 adapter owner 或 fail-closed 缺口；不得只回填 Evaluation/Interview 后就宣称 Persistence 已闭合。

Migration 规则：
- 先检测窗口 22 已创建的版本号和名称；禁止重复版本号。
- `V010__durable_stream_events.sql` 归窗口 32；本窗口不得抢先创建或覆盖 V010。
- 因所有 migration 尚未执行，优先在交接记录中说明“修正现有 Draft”还是“追加兼容 Draft”；不能自行声称生产迁移不可变。
- 所有跨表引用使用 tenant_id + resource_id 组合 FK；核心唯一约束也包含 tenant_id。
- JSONB 只用于固定 snapshot/list 输出，不逃避 state/version/owner/FK/unique。
- 不插入真实业务数据、默认管理员、Provider 配置、价格、Prompt 或 secret。

只读静态检查：
- 每个最新 repository port 都有明确实现或显式缺口。
- Entity/domain 映射没有旧枚举、旧字段或静默默认值。
- migration 版本唯一，核心表 tenant/owner/version/timestamp/index/FK/unique 完整。
- 无 PaiCLI 引用、无真实 secret、无敏感正文日志。

不得运行构建、测试、迁移、服务、数据库、Git 或外部调用。

交接必须列出：
- 修改文件与 migration 版本表。
- 旧字段到新字段的迁移/废弃映射。
- 表、schema owner、tenant/owner 约束矩阵。
- 仍缺的 application port 或 Adapter。
- 未执行项与证据状态 NotRun。
```
