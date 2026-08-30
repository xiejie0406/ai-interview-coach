# FEAT-QBANK-001 若依版本化面试题库闭环

> 文档类型：Feature 控制页
> 文档状态：Draft（V2 重写中，尚未 Approved）
> 风险等级：L3
> 当前阶段：8 审查验证
> 阶段状态：Blocked（局部运行证据已取得）
> 证据结果：Blocked（真实登录态、Provider 和用户 UAT 未完成）
> 更新时间：2026-08-30

本 Feature 是若依 AI 面试 V2 的题库垂直切片：匿名/登录用户浏览已发布题目，登录用户保存个人答案，题库编辑与审核角色在 `admin-web` 完成草稿、版本、Rubric、审核、发布和下线。普通用户不能直接创建公共题目；公共内容必须经过后台治理工作流。

| 产物 | 路径 | 状态 |
|---|---|---|
| 产品事实 | [docs/product/prd.md](../../product/prd.md) | Draft |
| Feature Spec | [feature-spec.md](feature-spec.md) | Draft |
| 技术设计 | [design.md](design.md) | Draft |
| 任务包 | [tasks.md](tasks.md) | Draft |
| 验证记录 | [verification.md](verification.md) | Blocked（局部 Pass） |
| 用户验收 | [acceptance.md](acceptance.md) | InProgress / Blocked（等待用户确认） |
| HTML 证据报告 | [evidence/verification-report.html](evidence/verification-report.html) | Draft（局部证据汇总） |
| 收敛方案 | [../../architecture/ruoyi-qbank-voice-convergence-plan.md](../../architecture/ruoyi-qbank-voice-convergence-plan.md) | Draft |
| 开发记录 | [../../development-records/2026-08-29-ruoyi-v2-implementation.md](../../development-records/2026-08-29-ruoyi-v2-implementation.md) | Draft |

## 追溯

`REQ-QBANK-01..07 -> BR-QBANK-01..12 -> AC-QBANK-01..09 -> DES-QBANK-01..04 -> TASK-QBANK-V2-01..07`

## V2 状态说明

- 旧版仅公开读取的实现记录仍保留作历史参考，但不代表 V2 已验证。
- 旧版“登录用户新增公共题目”的入口、接口和证据已废止；新增公共题目只能由 Admin 工作流完成。
- 当前已有 Java 17 目标构建、Flyway v10/v11、606 条题库回读和页面级检查证据；真实登录态 Admin、ASR/TTS/对象存储和用户 UAT 仍未完成，不能宣称整体通过。
