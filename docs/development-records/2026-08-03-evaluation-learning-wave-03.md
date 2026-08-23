# Evaluation / Learning Wave 03 开发记录

> 文档类型：开发记录 / Draft  
> 文档状态：Draft  
> 风险等级：L3  
> 当前主阶段：阶段 3 功能规格，WaitingForApproval  
> 证据结果：NotRun  
> 更新时间：2026-08-03  

## 本轮范围

本轮目标只落地 Evaluation、Report、Learning 相关候选生产源码；不改变 PRD、技术架构截断原文、既有 Phase、POM、contracts、adapters、boot、frontend 或测试代码。协调窗口复核确认：Wave 3 提示词和代码落地就绪审查在本窗口启动前已经存在，不属于本窗口新增产物；本开发记录是该窗口唯一越出源码范围的文件，保留用于如实审计该偏差。

## 已写入内容

- 复用了启动前已存在的代码落地就绪审查与窗口 21–26 提示词，没有重新创建或改写它们。
- 新增 Evaluation domain：`EvaluationRun`、`EvaluationReport`、Evidence、Rubric、Prompt/Schema pin、Provider policy snapshot、feedback 等模型。
- 新增 Learning domain：`LearningPlan`、Goal、Task、Checkpoint、Recommendation、Progress snapshot 等模型。
- 新增 Agent application ports：Evidence Extractor、Rubric Judge、Report Composer、Learning Coach。
- 新增 Evaluation application：repository/access port、start evaluation、pipeline、report read、feedback append 默认实现。
- 新增 Learning application：repository/source access、create/read/complete task 默认实现。

## 已执行的只读检查

- 统计目标范围内 Java 文件：42 个。
- package 声明存在。
- Maven module `src/main/java` 根下 import 目标可定位。
- 目标范围未发现 Spring/JPA/HTTP framework import。
- 目标范围未发现 PaiCLI / paicli-main / com.paicli 引用。
- toString 敏感字段抽查未命中明文 answer/transcript/prompt/sourceContentRef/feedbackRef/reportBodyRef/contentRef 泄漏模式。

## 未执行项

- 未运行 Maven 编译、测试、服务启动或 migration。
- 未新增测试代码。
- 未运行真实 LLM/ASR/TTS/支付/对象存储调用。
- 未执行 Git add/commit/push/PR。

## 已知限制

- 当前仅为候选实现，不代表 Feature tasks.md Approved。
- Evaluation pipeline 仍依赖后续 adapter/worker 装配，未接真实 Provider。
- 协调窗口的随后交叉审查发现：Evaluation 状态名、Evidence/Rubric 结构、Report Composer 输出、feedback target、Learning 状态/任务模型尚未与公共 JSON Schema/OpenAPI 对齐；StartEvaluation 也未返回 durable Job/operation receipt。本波源码只能视为待修正骨架，不能直接交给 REST/持久化 owner 当作冻结接口。
- Report ID 映射、Learning plan 列表、Voice ACK/flow-control/cancel、Billing minor unit、Export status、Admin Audit 等公共契约已由协调窗口形成更具体候选，但 Adapter 和运行证据仍未完成。
- 未经编译验证，仍可能存在 Java 语法或装配层问题；证据保持 NotRun。

## 协调窗口合并后的补充复核

在本窗口第一次交接后，协调窗口继续补齐了 durable Job receipt、Evaluation policy/source access、更多 Evaluation/Learning 用例和状态投影。2026-08-03 再次只读复核时，目标范围共有 65 个 Java 文件；未发现重复 fully-qualified type，Maven module `src/main/java` 根下 import 目标可定位，未发现 PaiCLI、Spring/JPA/HTTP 反向依赖或明显敏感 `toString` 泄漏。该检查仍不等同于编译或测试，证据继续保持 `NotRun`。

## Learning application 契约收口补充

协调复核继续发现旧 `DefaultCreateLearningPlan` 仍引用已经退出公共模型的 `DRAFT`、`goals/tasks/recommendation` 与 `canDerive`，且 confirm/cancel、item command、列表和 dashboard 只有接口没有默认用例。当前候选源码已完成以下收口：

- Create Learning Plan 改为短事务 claim 幂等记录、事务外调用 Learning Coach、最终短事务重验 active principal、ReportVersion 和 QuestionVersion allowlist，再保存 `CANDIDATE` Plan、Domain Event 与幂等结果。
- 模型生成的 candidate item ID 不进入权威数据；每个 Learning Item ID 都由服务端重新生成。
- Learning Plan 固定 `sourceReportVersionId`、`configVersionId`、Prompt/Schema pin 与 Provider route snapshot；历史 Plan 不读取当前激活配置重算。
- Prompt/Schema pin 与 Provider route snapshot 的生产引用已收口到共享 `domain.platform` 值对象，Learning 不再依赖 Evaluation 逻辑域；原 `domain.evaluation` 同名类型仅 deprecated 暂留，未删除或移动文件。
- 新增 confirm/cancel、complete/skip/reschedule、owner-scoped list 和 dashboard 默认实现；旧 `CompleteLearningTask` 只保留为 deprecated 兼容入口，不应进入新的 REST/Boot wiring。
- `weaknessRef`、limitation 和 reason code 增加边界校验；Learning Coach 候选及 Learning Item 的 `toString` 不输出 weakness、limitation、contentRef 或 reasonCodes 正文。
- 空 items 是 `learning-plan-v1` 允许的合法候选；确认后立即进入 `COMPLETED`，避免永久停留在 `CONFIRMED`。

四个结构化输出契约到强类型候选模型的映射如下：

| JSON Schema | Provider-neutral port 输出 | 权威领域模型 |
|---|---|---|
| `evidence-extraction-v1.schema.json` | `EvidenceExtractorPort.Result` / `EvidenceBundle` | `EvidenceItem`：answerVersion/hash、type、offset、quoteHash、reasonCode；不保存引文正文 |
| `rubric-judgement-v1.schema.json` | `RubricJudgePort.Result` | `RubricScore` / `RubricDimensionScore`：categorical judgement、confidence、insufficientEvidence、reasonCodes、evidenceIds、limitations |
| `report-composition-v1.schema.json` | `ReportComposerPort.Result` / `ReportComposition` | immutable `ReportVersion` + `EvaluationReport`；应用层验证 Evidence/Judgement 引用后才发布 |
| `learning-plan-v1.schema.json` | `LearningCoachPort.Candidate` | `LearningPlan` + `LearningTask`；ReportVersion/config/prompt/schema/provider 固定，QuestionVersion 只取服务端 allowlist |

当前仍需后续 owner 承接：

- Persistence：`EvaluationRepository`、`LearningPlanRepository`、Report/Feedback append-only、Learning Item version 和固定策略快照映射。
- REST：OpenAPI DTO、ETag/If-Match、Idempotency-Key、owner-scoped 404 和错误码映射。
- Provider adapters：四个 Agent port 的 schema validation、主备路由、超时和受控失败码；不得把 Provider 原文写入异常或日志。
- Boot：上述默认用例、policy/source/dashboard/content/stream ports 与 Transaction/Job/Outbox 的 composition-root wiring。

本次只读静态检查覆盖全后端 370 个 Java 文件：重复 FQN 为 0、package/path mismatch 为 0、项目内部普通 import 缺失为 0；Evaluation/Learning 目标范围当前为 69 个 Java 文件，未发现旧 Learning 状态/方法引用、framework 反向依赖或 PaiCLI 引用。未运行编译或任何测试，证据仍为 `NotRun`。
