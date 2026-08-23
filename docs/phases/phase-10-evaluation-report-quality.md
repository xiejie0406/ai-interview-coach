# 第 10 期：评测、报告与质量门纵切

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-10  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：evaluation / content quality / frontend  
> 证据结果：NotRun  
> 前置：PHASE-04、06、08、09；评分措辞与质量门批准

## 1. 本期为什么存在

文本面试只有在产生可追溯、可拒判、版本化的会后报告时才形成核心价值。将评测和报告放在同一纵切，可防止报告层重新解释或篡改 Judge 事实。

## 2. 用户可见目标

面试结束后看到报告进度和逐题证据、技术/结构维度、限制、三项行动建议；证据不足明确显示，用户可反馈不准确。

## 3. 技术学习目标

学习异步 evaluation pipeline、Evidence/Judge/Composer 职责、immutable ReportVersion、Golden Set 发布门、可比较指标和 human review。

## 4. 范围

面试 EvaluationJob、逐题 evidence/judge、维度聚合、Report Composer、ReportVersion、质量 Flag、Golden Set runner/report、进度 SSE、报告/纠错 UI。

## 5. 非目标

不做招聘结论/权威总分、不做公开分享、不生成长期 LearningPlan、不用语音表达指标、不重算历史报告。

## 6. 前置决策

DEC-013/031/038/039；维度/展示、拒判语义、Golden Set 门、报告部分完成和用户纠错范围批准。

## 7. 前置期次和依赖

04 Rubric version；06 Evidence/Judge；08 Job；09 completed turns。Report 只能消费 EvaluationVersion。

## 8. 涉及的 REQ/BR/AC/DES

REQ-06/07/08；BR-01/03–05；AC-02/07/08；DES-EVAL-PIPELINE/REPORT/QUALITY-GATE。

## 9. 本期完整功能点

EvaluationJob/版本；逐题 Evidence/Judge；冲突/不足；维度汇总；Composer 不改结论；报告进度/失败；用户反馈/重评新版本；Golden regression 与启用 Flag。

## 10. 正常流程

Session COMPLETING→Job 读取不可变输入→逐题评测→聚合→Composer→保存 ReportVersion→session/report 独立状态→SSE 完成→用户打开/反馈。

## 11. 空态、错误、拒绝、取消和恢复

零有效回答生成“数据不足”报告或按批准规则拒绝；单题失败允许部分报告并列限制；质量门未过不启用；用户取消 pending；Worker 崩溃 lease 恢复；重评创建新版本。

## 12. 后端模块

evaluation domain `EvaluationVersion/DimensionResult/EvidenceSpan/ReportVersion/QualityGateDecision`；application `EvaluateInterview/ComposeReport/ActivateEvaluationConfig`；adapters persistence/provider；Job worker。

## 13. 前端页面和组件

`/app/interviews/:id/report`、`/app/reports/:id`；`ReportProgress`、`EvidenceQuote`、`DimensionResult`、`ConfidenceNotice`、`ActionSuggestions`、`FeedbackDialog`。

## 14. 数据实体、约束和迁移

扩展 evaluation：`evaluation_job/input_snapshot/dimension_result/evidence_span/report_version/config_activation/user_feedback`。Report 引用 session/question/rubric/prompt/schema/model/evaluation 版本；不可变；用户删除走后续治理。

## 15. REST/SSE/WebSocket/API 或事件

`GET /interviews/{id}/report`、`GET /reports/{id}`、feedback/re-evaluate（受权）命令；SSE `evaluation.progress|report.ready|report.failed`；事件 payload 用引用，不含全文。

## 16. Agent/Prompt/Provider

Evidence Extractor/Rubric Judge 复用 06；Report Composer 只能组织裁决事实；每个角色独立 Prompt/Schema/模型配置，质量门分别评估。

## 17. 安全与隐私

报告用户私有；evidence 最小引用；运营只读质量/数量投影不见正文；反馈/重评审计；不把回答/报告放日志/trace。

## 18. 计划新增文件树

```text
interview-domain/.../evaluation/{ReportVersion,QualityGateDecision}.java
interview-application/.../evaluation/{EvaluateInterview,ComposeReport,ActivateEvaluationConfig}.java
interview-adapters/.../{persistence/evaluation,inbound/rest/report,inbound/sse/evaluation}/
interview-boot/.../{prompts/report-composer,db/migration/V###__extend_evaluation_report.sql}
frontend/src/features/evaluation/report/{pages,components,hooks,api}/
contracts/{openapi/evaluation-report.yaml,asyncapi/evaluation-events.yaml,schemas/report-composition-v1.schema.json}
benchmarks/runners/golden-set/; benchmarks/reports/
```

## 19. 计划修改文件树

```text
interview-application/.../agent/{EvidenceExtractor,RubricJudge}.java
interview-application/.../interview/session/
frontend/src/features/interview/room/TextInterviewPage.tsx
benchmarks/fixtures/golden-set/
```

## 20. 后续 TASK 拆分建议

评测 job/版本、聚合/Composer、质量门/runner、API/SSE、报告 UI、反馈/重评、Golden/安全验证；人工标注和真实模型分别授权。

## 21. 验证建议

单元：证据/聚合/Composer invariants；集成：Job/版本/部分失败；Provider contract；UI：progress/不足/失败；Golden Set：核心；UAT：逐题证据与三建议。不能用 Judge 自评证明 Judge。

## 22. 明确完成标准

报告全部结论可追溯；Composer 不改分；不足明确；历史版本可打开；未过门配置不能 ACTIVE；失败/恢复/反馈可观察。

## 23. 本期不能证明什么

不能证明学习效果、招聘预测、语音表达、长期模型漂移或生产一致性。

## 24. 风险与停止条件

无证据仍高置信、单一总分遮蔽、历史覆盖、质量门可绕过、模型自评代替人工或报告泄隐私时停止。

## 25. 下一期进入条件

稳定 Weakness/DimensionResult/ReportVersion 可供 Learning 只读消费；文本报告 UAT 无阻断歧义。

## 26. 建议学习和复盘内容

复盘 evaluation architecture、LLM judge calibration、golden regression、immutable reports、confidence communication 与 AI 质量责任。
