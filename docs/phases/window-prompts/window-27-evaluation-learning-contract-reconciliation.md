# 窗口 27 提示词：Evaluation / Report / Learning 契约收口

```text
你负责修正 AI Interview Coach 已有 Evaluation / Report / Learning 候选骨架，使它与现有公共 JSON Schema、OpenAPI 和架构不变量一致。不要把本任务写成已批准或已验证。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

事实边界：
- 项目与 PaiCLI 完全独立。
- L3；主阶段仍为阶段 3 WaitingForApproval；运行证据全部 NotRun。
- PRD 与 technical-architecture 各有截断，禁止修改或猜补。
- 窗口 21 已产生骨架，但静态交叉审查发现公共契约不一致；必须把它当待修正输入，不能当完成事实。
- 窗口 31 必须已经交接，Evaluation 的 durable Job/Idempotency 只能依赖其收口后的公共 platform 边界。

只允许编辑：
- backend/interview-domain/src/main/java/com/aiinterviewcoach/domain/evaluation/**
- backend/interview-domain/src/main/java/com/aiinterviewcoach/domain/learning/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/evaluation/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/learning/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/agent/evidence/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/agent/judge/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/agent/report/**
- backend/interview-application/src/main/java/com/aiinterviewcoach/application/agent/learning/**

禁止修改：
- contracts、frontend、adapters、boot、POM、migration、测试代码、docs/product、docs/architecture/technical-architecture.md、docs/phases/phase-*.md。

必须以这些文件为输入事实：
- contracts/schemas/evidence-extraction-v1.schema.json
- contracts/schemas/rubric-judgement-v1.schema.json
- contracts/schemas/report-composition-v1.schema.json
- contracts/schemas/learning-plan-v1.schema.json
- contracts/openapi/evaluation-report.yaml
- contracts/openapi/learning.yaml
- docs/architecture/implementation-contract-pack.md

必须修正：
1. Evidence 产物必须表达 answerVersionId、answerHash、type、offset、quoteHash、reasonCode；不得以 contentRef 代替 schema 字段，不保存原文。
2. Rubric Judge 使用 CORRECT/PARTIAL/INCORRECT/INSUFFICIENT/CONFLICTING、LOW/MEDIUM/HIGH、insufficientEvidence、reasonCodes、evidenceIds；不得擅自改成数值总分。
3. Report Composer 返回结构化 sections/actions/limitations 候选；Provider/Agent 不拥有持久化正文引用。应用层在 schema/引用/限制门通过后才生成不可变 ReportVersion 或受控 content port 引用。
4. Evaluation API 状态对齐 PENDING/RUNNING/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL/CANCELLED，并单列 stage；失败、人工复核和 schema invalid 不能写成成功。
5. StartEvaluation 必须通过现有 platform Job/Outbox/Idempotency 边界形成 durable OperationAccepted/Job receipt；不能只创建 Run 后返回 202 语义。
6. Feedback endpoint 以 evaluationId 为入口，服务端解析 report；反馈是 append-only，不覆盖历史评测或报告正文。
7. Report 必须可由 reportId 查询，也必须有 owner-scoped interviewId -> reportId 映射 port/query；禁止把 interviewId 当 reportId。
8. Learning 状态对齐 CANDIDATE/CONFIRMED/COMPLETED/CANCELLED；Item 状态对齐 PENDING/IN_PROGRESS/COMPLETED/SKIPPED/CANCELLED，支持 skip/reschedule，保留 item version。
9. Learning Coach 只能从 report snapshot + allowlisted catalog question versions 生成 learning-plan-v1 候选；外部命令不得直接提交模型产物、goals/tasks 作为权威事实。
10. 补齐 list/get learning plans、dashboard projection DTO/port，以及刷新恢复所需 tenant/owner/version 约束；projection stale 必须显式。
11. Prompt/Schema/Provider route/config version 全部 pin；历史版本不因激活配置变化而重算。
12. 所有回答、证据正文、报告正文、Prompt/模型内容的 toString 必须脱敏；事件只含稳定引用/版本/状态/原因码。

实现原则：
- domain/application 继续 framework-free、provider-neutral。
- 不为迁就旧骨架保留两套并行权威模型；若旧类型必须暂留，明确 deprecated 且生产用例不得再引用。
- 缺少公共端口时在允许目录新增 consumer-owned port，不越界实现 adapter。
- 不运行构建、测试、服务、迁移、Provider 或 Git。

只读交接：
- 列出旧模型到新模型的映射。
- 列出每个 OpenAPI operation 对应 use case。
- 列出四个 JSON Schema 对应的强类型输入/输出。
- 检查 package/path/import、domain/application 反向依赖、tenant/owner/version、敏感 toString。
- 明确所有运行证据仍为 NotRun，以及尚需 persistence/REST/Boot owner 处理的端口。
```
