# AI Interview Coach

> 项目状态：RuoYi 单平台收敛中
> 文档状态：Draft
> 风险等级：L3
> 当前阶段：8 审查验证 / 9 用户验收
> 更新时间：2026-08-30

面向 Java 开发者转型 AI 应用与 Agent 开发的面试训练系统。账号、Token、菜单权限和业务后端已经统一到 RuoYi。

## 当前工程

| 目录 | 职责 |
|---|---|
| `platform-backend/` | 唯一 RuoYi Java 后端；`ruoyi-admin` 聚合 `ruoyi-interview` |
| `admin-web/` | RuoYi 管理端 |
| `portal-web/` | 用户 Portal Web |
| `mobile/` | 移动端工程；V2 原生语音延期 |
| `ruoyi-app/` | RuoYi-App 上游基线 |
| `ruoyi-backend/` | RuoYi 后端上游基线 |
| `ruoyi-vue3-frontend/` | RuoYi-Vue3 上游基线 |
| `contracts/` | OpenAPI / AsyncAPI 契约 |
| `docs/` | 规格、设计、验证和迁移记录 |

旧 `frontend/`、`backend/` 已于 2026-08-30 删除；原 `apps/` 下的七个工程已提升到仓库根目录，`apps/` 已删除。历史文档中的 `apps/...` 路径表示提升前的目录结构。

## 本地入口

- 后端：`platform-backend/bin/run-local.ps1`，默认端口 `8081`
- Admin：在 `admin-web/` 执行 `npm run dev -- --host 127.0.0.1`，默认端口 `5173`
- Portal：在 `portal-web/` 执行 `npm run dev -- --host 127.0.0.1`，默认端口 `5174`

## 当前边界

已迁入并取得局部运行证据：RuoYi 登录权限、题库、文字面试，以及 Portal Web 语音链路代码。真实 Provider、对象存储、完整 WS/SSE 联机、Admin 全工作流和业务 owner 最终 UAT 仍需单独结论。

明确不在当前 V2 范围：评测报告、学习计划/Dashboard、单题练习、计费支付、隐私导出/删除、运营状态和原生移动端语音。

## 文档入口

- [迁移状态与待办](docs/reference/migration-status-and-todo.md)
- [RuoYi 题库与语音收敛计划](docs/architecture/ruoyi-qbank-voice-convergence-plan.md)
- [旧项目删除记录](docs/development-records/2026-08-30-legacy-project-convergence.md)
- [根目录提升记录](docs/development-records/2026-08-30-apps-root-convergence.md)
- [项目规范索引](docs/specs/README.md)
