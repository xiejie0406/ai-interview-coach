# 第 11 期：Learning Coach、复练与 Dashboard

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-11  
> 风险等级：L2  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：learning / practice / frontend  
> 证据结果：NotRun  
> 前置：PHASE-05、10

## 1. 本期为什么存在

报告若不能进入下一轮行动只是一次性反馈。Learning 域把版本化弱项映射到真实题目/题单和复测，完成题库→练习/面试→报告→复练闭环。

## 2. 用户可见目标

用户从报告接受/修改三个训练建议，在 Dashboard 看到今日任务，完成专项练习/跳过/改期并查看可比较的复测变化。

## 3. 技术学习目标

学习 recommendation as candidate、确定性校验、任务状态、历史可比性、Dashboard read model 和可解释推荐。

## 4. 范围

Weakness、LearningPlan/Item、Learning Coach 候选、用户编辑、题目存在校验、到期/完成/跳过/改期、复测、可比趋势、Dashboard/最近报告。

## 5. 非目标

不做全站推荐算法、向量检索、羞辱性 streak、公开分享、JD/简历个性化或 B2B 分配。

## 6. 前置决策

P0 Dashboard/学习计划字段、建议数量、用户编辑、趋势可比与数据不足文案批准。

## 7. 前置期次和依赖

05 Practice；10 Evaluation/Report。Learning 不直接访问 evaluation Repository，通过公开只读 DTO/event。

## 8. 涉及的 REQ/BR/AC/DES

REQ-10，支持 REQ-07/08；AC-07；BR-04；DES-MOD-08、DES-LEARNING-PLAN/COMPARABILITY。

## 9. 本期完整功能点

从弱项生成候选；校验题目存在/发布；用户接受/编辑；LearningItem 状态；专项练习入口；复测关联；趋势可比；Dashboard 今日任务/最近报告/空态/推荐解释。

## 10. 正常流程

Report ready→Learning Coach 生成候选→确定性过滤→用户确认→Dashboard 展示→打开 Practice→完成/复测→更新计划与可比趋势。

## 11. 空态、错误、拒绝、取消和恢复

无可靠弱项显示“证据不足”；无可用题不给虚假链接；用户拒绝/删除建议；跳过/取消保留原因可选；题目下线重新推荐；加载失败保留计划；不可比版本不画趋势。

## 12. 后端模块

domain `Weakness/LearningPlan/LearningItem/ComparabilityPolicy`；application `GenerateLearningPlan/ConfirmPlan/CompleteItem/BuildDashboard`；adapters persistence/read projection。

## 13. 前端页面和组件

`/app`、`/app/learning`、`/app/learning/:id`；`TodayTasks`、`RecentReportCard`、`LearningItemCard`、`RecommendationReason`、`ProgressTrend`、`InsufficientEvidence`。

## 14. 数据实体、约束和迁移

`learning.weakness_ref/learning_plan/learning_item/retest_link/dashboard_projection`。弱项引用 evaluation/version/evidence；item 引用 catalog version；状态/版本/tenant；趋势存比较结果与 reason，不覆盖历史。

## 15. REST/SSE/WebSocket/API 或事件

Learning plan create/confirm/update/items/complete/skip/reschedule、Dashboard query；消费 `report.ready/practice.completed`；普通 REST 足够，可选 SSE 更新进度但不必新建协议。

## 16. Agent/Prompt/Provider

Learning Coach 输出候选 schema；只能引用允许 question IDs 和弱项 evidence；确定性去重/数量/可用性门；Provider 失败允许规则型计划或稍后重试。

## 17. 安全与隐私

学习档案私有；Dashboard 投影 tenant scope；推荐不暴露 Prompt/其他用户数据；用户可删改；不使用敏感录音正文。

## 18. 计划新增文件树

```text
interview-domain/.../learning/{Weakness,LearningPlan,LearningItem,ComparabilityPolicy}.java
interview-application/.../learning/{GenerateLearningPlan,ConfirmLearningPlan,CompleteLearningItem,BuildDashboard}.java
interview-adapters/.../{persistence/learning,inbound/rest/learning}/
interview-boot/.../{prompts/learning-coach,db/migration/V###__create_learning.sql}
frontend/src/features/learning/{pages,components,hooks,api}/
frontend/src/features/dashboard/{pages,components,hooks}/
contracts/{openapi/learning.yaml,schemas/learning-plan-v1.schema.json}
```

## 19. 计划修改文件树

```text
frontend/src/app/router.tsx
frontend/src/features/evaluation/report/ActionSuggestions.tsx
frontend/src/features/practice/
contracts/asyncapi/evaluation-events.yaml
```

## 20. 后续 TASK 拆分建议

领域/迁移、Coach/schema、确定性校验、计划用例、Dashboard projection/UI、复测/趋势、验证/UAT。

## 21. 验证建议

单元：可比/状态/过滤；集成：事件/tenant/下线题；Provider contract；UI：空/拒绝/改期；Golden Set：建议相关性与虚构 ID；UAT：报告→复练。不能证明七日留存。

## 22. 明确完成标准

可靠弱项能形成可编辑、可执行计划；建议只引用真实题；完成/跳过/复测状态正确；不可比时不误导；Dashboard 有空/恢复状态。

## 23. 本期不能证明什么

不能证明学习效果、留存、个性化最优、推荐公平或付费价值。

## 24. 风险与停止条件

Coach 虚构内容、趋势跨不兼容版本、推荐不可解释/不可关闭、Learning 重算评测或投影跨 tenant 时停止。

## 25. 下一期进入条件

文本 T1 闭环成立；Voice 可只改变 Answer 输入和表达指标，不改变 Learning/Report 事实边界。

## 26. 建议学习和复盘内容

复盘 recommendation guardrails、read models、event-driven projection、temporal comparability、用户控制与可解释学习闭环。
