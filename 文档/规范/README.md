# 工作区文档规范索引

> 文档类型：项目规范索引  
> 规范版本：2026.09-r4  
> 文档状态：Approved  
> owner：用户维护工作项目方向；Agent 按实际工程维护项目事实  
> 批准记录：2026-09-10 用户要求整理项目规范；2026-09-11 用户批准文档中文命名；2026-09-12 用户要求按若依、面试、智能选品、智能体桌面端和生产排产五类归档  
> 更新时间：2026-09-12  
> 适用范围：本工作区全部项目文档

## 规则优先级

1. 当前会话中更高优先级的明确指令。
2. `C:\Users\admin\.codex\AGENTS.md` 和 `C:\Users\admin\.codex\specs\` 的 Approved 用户级规范。
3. 仓库根 [`../../AGENTS.md`](../../AGENTS.md) 和本目录的 Approved 项目规范。
4. 已批准的项目、Feature、设计、契约和执行包。
5. 当前代码、配置和实际证据只说明现状，不自动批准产品行为。

## 工作区规范

| 规范 | 职责 |
|---|---|
| [`../../AGENTS.md`](../../AGENTS.md) | 工作区事实、加载顺序、数据边界和最低检查 |
| [`文档组织规范.md`](文档组织规范.md) | 五类目录、事实唯一归属、命名、归档和迁移要求 |
| [`../README.md`](../README.md) | 当前文档总入口，不维护规则正文 |

## 用户级规范路由

相对路径均位于 `C:\Users\admin\.codex\specs\`。

| 任务 | 用户级事实源 |
|---|---|
| 风险、阶段门、任务拆分 | `core/workflow.md` |
| 授权、测试、Git、上线 | `core/authorization.md` |
| 证据记录与结果 | `core/evidence.md` |
| 文档状态与历史保护 | `core/documentation.md` |
| 调研与 PRD | `disciplines/product.md` |
| Feature、需求和验收条件 | `disciplines/feature.md` |
| 架构、数据和契约 | `disciplines/architecture.md` |
| 源码、配置、依赖和集成 | `disciplines/development.md` |
| 前端与浏览器实现 | `disciplines/frontend.md`、`disciplines/ui-ux.md` |

## 当前分类入口

| 分类 | 入口 |
|---|---|
| 若依框架 | [`../若依框架/README.md`](../若依框架/README.md) |
| 面试项目 | [`../项目/面试项目/README.md`](../项目/面试项目/README.md) |
| 智能选品项目 | [`../项目/智能选品项目/README.md`](../项目/智能选品项目/README.md) |
| 智能体桌面端项目 | [`../项目/智能体桌面端项目/README.md`](../项目/智能体桌面端项目/README.md) |
| 生产排产项目 | [`../项目/生产排产项目/README.md`](../项目/生产排产项目/README.md) |
| 跨项目开发与迁移记录 | [`../开发记录/README.md`](../开发记录/README.md) |
| 跨项目历史原文 | [`../归档/README.md`](../归档/README.md) |
