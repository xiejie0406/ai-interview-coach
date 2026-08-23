# 第 06 期：Provider SPI 与单题证据反馈纵切

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-06  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：agent / evaluation / integration / frontend  
> 证据结果：NotRun  
> 前置：PHASE-02、04、05；Provider/Prompt 相关决定批准

## 1. 本期为什么存在

把 T0 原型收敛成产品内最小 AI 纵切：稳定 SPI、版本化 Prompt/Schema、证据提取、Rubric Judge 与拒判门。先在单题上形成可回放反馈，再扩到长面试。

## 2. 用户可见目标

提交单题回答后，用户看到引用原话的“正确/部分/错误/证据不足”逐点反馈，并可标记不准确；Provider 失败时保留回答并显示可重试/稍后处理。

## 3. 技术学习目标

学习 ports/adapters、schema-first LLM、Prompt registry、证据校验、主备路由、有限重试、成本元数据与 AI 内容质量门。

## 4. 范围

`ChatModelPort`、主 Adapter+fake（备 Adapter 依决定）、Prompt/Schema registry、Evidence Extractor、Rubric Judge、确定性后处理、EvaluationVersion、反馈 UI、用户纠错、调用元数据。

## 5. 非目标

不实现面试 Agent 状态编排、不生成综合报告/趋势、不做 ASR/TTS、不宣传权威评分、不自动启用未过 Golden Set 的版本。

## 6. 前置决策

DEC-013、030、031、034、038、039、048、058；Phase 02 的结果必须可定位，结构化/质量门由评测设计批准。

## 7. 前置期次和依赖

02 benchmark/schema；04 Question/Rubric version；05 AnswerVersion。只通过公开 port/DTO 读取。

## 8. 涉及的 REQ/BR/AC/DES

REQ-07/08/15，支持 REQ-04；BR-01/03/04；AC-02/08；DES-MOD-06/07/11、DES-PROVIDER、DES-EVAL。

## 9. 本期完整功能点

Provider SPI/错误分类；Prompt/Schema 生命周期；调用账本；EvidenceSpan 回读；Judge schema/置信门；质量 Flag；反馈/纠错；同一输入版本幂等评测；主失败受控切备/失败。

## 10. 正常流程

AnswerVersion 提交→创建 Evaluation request→抽取 EvidenceSpan→校验原文→Judge 对 Rubric 维度判断→确定性门裁决→保存 EvaluationVersion→前端显示反馈。

## 11. 空态、错误、拒绝、取消和恢复

短/空回答可直接 evidence insufficient；坏 JSON/虚构 span 拒绝；安全/条款/额度拒绝不重试；用户取消 pending job；Provider 超时保持 pending/failed retryable；重试引用同一输入且不覆盖历史。

## 12. 后端模块

application `ChatModelPort/ProviderRouter/EvidenceExtractor/RubricJudge/EvaluateAnswer`；domain evaluation objects；adapters LLM primary/fake、evaluation persistence；boot prompt resources/Provider config。

## 13. 前端页面和组件

Practice 页增加 `EvaluationStatus`、`EvidenceFeedback`、`ConfidenceNotice`、`FeedbackCorrectionDialog`；hooks 轮询/SSE 留待 Job 设计，首期可 REST 查询明确状态。

## 14. 数据实体、约束和迁移

`agent.prompt_version`、`schema_version`、`provider_invocation`；`evaluation.evaluation_version`、`dimension_result`、`evidence_span`、`user_feedback`。输入/模型/prompt/schema/rubric 版本不可空；正文最小化。

## 15. REST/SSE/WebSocket/API 或事件

`POST /practice-attempts/{id}/evaluations`（幂等）、`GET /evaluations/{id}`、`POST /evaluations/{id}/feedback`；事件 `evaluation.requested|completed|failed`，具体异步可靠性在 08。

## 16. Agent/Prompt/Provider

本期正式拥有 Evidence Extractor/Rubric Judge；Interview Agent 只复用 SPI 不实现。Prompt/Schema ACTIVE 需 Golden Set 门；主备不能跨违反 data-region 的 Provider。

## 17. 安全与隐私

只向 Provider 发送必要题干/Rubric/回答片段；记录 request ID/版本/用量不记 Key/完整 prompt/answer；Prompt Injection 样本；用户反馈为私有。

## 18. 计划新增文件树

```text
interview-application/.../agent/port/{ChatModelPort,ModelRequest,ModelResponse,ProviderFailure}.java
interview-application/.../agent/{EvidenceExtractor,RubricJudge,ProviderRouter}.java
interview-domain/.../evaluation/{EvaluationVersion,DimensionResult,EvidenceSpan}.java
interview-adapters/.../{provider/llm/{primary,fake},persistence/evaluation}/
interview-boot/.../{prompts/evidence, prompts/rubric-judge, db/migration/V###__create_agent_evaluation.sql}
frontend/src/features/evaluation/{components,hooks,api}/
contracts/{openapi/evaluation.yaml,schemas/{evidence-extraction-v1,rubric-judgement-v1}.schema.json}
```

## 19. 计划修改文件树

```text
frontend/src/features/practice/pages/PracticePage.tsx
interview-application/.../practice/SubmitAnswer.java
benchmarks/fixtures/golden-set/
docs/reference/provider-and-prompt-versioning.md
```

## 20. 后续 TASK 拆分建议

SPI/路由、Prompt registry、evidence、judge、persistence/API、feedback UI、Golden gate、Provider contract/安全各拆 TASK；真实 Provider 与费用为独立授权项。

## 21. 验证建议

单元：span/schema/后处理；集成：事务/版本；契约：Provider timeout/rate limit/bad JSON；UI：pending/fail/insufficient；Golden Set：核心；UAT：反馈可读/可纠错。真实质量不能由 fake 证明。

## 22. 明确完成标准

每个高置信结论有可回读 EvidenceSpan；无证据拒判；版本全可定位；未过门组合无法 ACTIVE；Provider 失败不丢回答且不无限重试。

## 23. 本期不能证明什么

不能证明整场面试评测、长期一致性、生产 Provider SLA、学习效果或招聘准确性。

## 24. 风险与停止条件

DPA/地域不满足、框架类型泄漏 domain、模型虚构证据无法拦截、Golden Set 低于门、成本无界或日志正文泄漏时停止。

## 25. 下一期进入条件

ChatModelPort、ProviderFailure、Prompt/Schema 版本和最小 Evaluation 契约稳定，可供 Interview Agent 与成本预估复用。

## 26. 建议学习和复盘内容

复盘 deterministic guardrails、LLM-as-judge 限制、schema evolution、model routing、prompt regression 和“内容质量≠链路正确”。
