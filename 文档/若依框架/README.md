# 若依框架资料入口

本目录只维护若依上游基线、共享工程入口和各业务项目的集成导航，不接管任何项目专属的产品、数据或 Feature 事实。

## 上游与共享工程

| 内容 | 入口 | 边界 |
|---|---|---|
| 若依后端上游基线 | [工程](../../ruoyi-backend/_reference/ruoyi-original/)、[说明](../../ruoyi-backend/_reference/ruoyi-original/README.md)、[许可原文](../../ruoyi-backend/_reference/ruoyi-original/LICENSE) | 上游对照，不参与活跃 Maven 构建 |
| 若依 Vue 3 前端上游基线 | [工程](../../frontend/_reference/ruoyi-vue3-frontend/)、[说明](../../frontend/_reference/ruoyi-vue3-frontend/README.md)、[许可原文](../../frontend/_reference/ruoyi-vue3-frontend/LICENSE) | 上游对照，不参与活跃前端构建 |
| 若依移动端上游基线 | [工程](../../miniapp/_reference/ruoyi-app/)、[说明](../../miniapp/_reference/ruoyi-app/README.md)、[许可原文](../../miniapp/_reference/ruoyi-app/LICENSE) | 上游对照，不参与活跃小程序构建 |
| 当前平台后端 | [`../../ruoyi-backend/`](../../ruoyi-backend/) | 平台身份、认证、角色、菜单和业务模块装配入口 |
| 当前管理端 | [`../../frontend/admin-web/`](../../frontend/admin-web/) | 当前管理端业务工程 |
| 当前门户端 | [`../../frontend/portal-web/`](../../frontend/portal-web/) | 当前用户门户业务工程 |
| 当前移动端 | [`../../miniapp/interview-mobile/`](../../miniapp/interview-mobile/) | 当前面试小程序工程 |
| 当前桌面端 | [`../../frontend/desktop/aden-desktop/`](../../frontend/desktop/aden-desktop/) | 当前 Electron 桌面工程 |

## 项目集成导航

- [平台密钥、AI 密钥与 AI 角色（三模块线框图及设计草案）](功能/FEAT-RUOYI-001-平台与AI基础设施/README.md)
- [四项目 Java 业务模块统一装配与启动](Java业务模块统一装配.md)
- [并行开发与验收隔离方案（待实施）](并行开发与验收隔离方案.md)
- [按技术类型归档架构](工程按技术类型归档架构.md)与[迁移设计](工程按技术类型归档设计.md)
- [工程目录迁移与恢复记录](../开发记录/2026-09-25-工程按技术类型目录迁移.md)
- [面试项目若依集成索引](面试项目集成索引.md)
- [智能体桌面端的若依控制面设计](../项目/智能体桌面端项目/功能/FEAT-ADEN-001-桌面执行底座/技术设计.md)
- [智能选品项目的 Java、若依与 Python 边界](../项目/智能选品项目/架构/技术架构设计.md)
- 生产排产项目尚未形成当前若依集成资料；不得从旧归档或其他项目推断其平台接入方式。

“若依相关”不等于“若依共享”。例如，面试题库、语音面试、PostgreSQL 业务数据和 `ruoyi-interview` 模块都属于面试项目；其他项目不得从这些文档推断自己的数据库或模块边界。
