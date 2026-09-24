# 若依完整代码落地与 AI Interview Coach 迁移基线

> 文档类型：迁移基线 / Canonical Architecture Baseline
> 文档状态：Draft
> 当前阶段：阶段 5 技术设计 / InProgress
> 风险等级：L3
> 创建日期：2026-08-16
> 证据结果：Pass（上游仓库可访问且完整 clone 已完成）

## 1. 目标

本基线确认本项目采用“完整若依代码落地 + 现有 AI Interview Coach 业务迁移”的路线：

1. 完整保留官方 RuoYi-Vue3 前端、RuoYi-Vue 后端和 RuoYi-App Vue3 移动端上游仓库，分别包含其完整源码、配置、脚本、文档和许可证。
2. 现有 `backend/` 与 `frontend/` 不被上游代码直接覆盖。
3. 逐步把现有 AI 面试业务迁移到若依工程体系中。
4. 后台管理、用户前台和移动端分别落到清晰的应用边界。
5. 迁移完成前保留旧代码可回退路径。

## 2. 已锁定的完整上游

| 项目 | 基线 |
|---|---|
| 上游 | 地址 / 分支 / commit | 完整落地目录 |
|---|---|---|
| RuoYi-Vue3 前端 | `https://github.com/yangzongzhuan/RuoYi-Vue3.git` / `master` / `3863cf391a7feba4a2b15b923d7d2dd6b9f62c77` | `apps/ruoyi-vue3-frontend/` |
| RuoYi-Vue 后端 | `https://github.com/yangzongzhuan/RuoYi-Vue.git` / `master` / `b5e14338e83ba4f932b6949bee8767a6a7220dbc` | `apps/ruoyi-backend/` |
| RuoYi-App 移动端 | `https://github.com/yangzongzhuan/RuoYi-App.git` / `vue3` / `930d5cd6e585f78bc0a49c0587535e8904fe4be8` | `apps/ruoyi-app/` |

三个目录均为完整上游 clone，只读基线；改造分别落到 `apps/admin-web/`、`apps/portal-web/` 和 `apps/mobile/`。之前的 `apps/ruoyi-upstream/` Vue2 错误 clone 已删除。

## 3. 目标工程布局

```text
apps/
  ruoyi-vue3-frontend/  # RuoYi-Vue3 完整、只读参考基线
  ruoyi-backend/        # RuoYi-Vue 完整、只读参考基线
  ruoyi-app/            # RuoYi-App Vue3 完整、只读参考基线
  admin-web/            # 若依前端改造 + AI 管理业务
  portal-web/           # 用户前台网页，Vue 3
  mobile/               # uni-app Vue 3，多端移动入口
backend/                # 现有 AI 业务后端，迁移期间保留
frontend-legacy/        # 旧 React 前台，迁移完成后归档
contracts/              # REST / SSE / WebSocket 契约
```

## 4. 后端迁移边界

第一阶段不直接用 RuoYi 后端替换现有 AI 业务后端。现有 `backend/` 继续承载：

- 面试领域与用例；
- Provider、ASR、TTS 和报告能力；
- 现有鉴权、Session、CSRF 和权限边界；
- Flyway 数据迁移；
- REST、SSE、WebSocket 契约。

若后续决定迁移到 RuoYi 后端，必须另行形成 L3 数据/API/鉴权迁移设计、回滚方案和批准执行包，不能把“完整若依代码落地”误写成“业务后端已经迁移完成”。

## 5. 三端迁移原则

- `admin-web`：完整使用若依前端骨架，迁移后台管理业务；不复制 RuoYi 默认后端鉴权实现。
- `portal-web`：自研 Vue 3 用户前台，复用业务 API、类型和 Pinia 逻辑，不复制后台视图。
- `mobile`：uni-app Vue 3；录音、上传和小程序能力走适配层，不直接访问 Provider。
- `frontend/`：迁移完成前保持原位；完成用户验收后优先归档为 `frontend-legacy/`，不直接删除。

## 6. 禁止误读

- 三个 RuoYi 上游目录完整存在，不代表 AI 业务已经迁移完成。
- 上游仓库完整存在，不代表可以直接覆盖现有 `backend/` 或 `frontend/`。
- 文件存在、clone 成功或构建成功，均不能替代 Feature 验收和用户验收。
- 原文档中 `gitee.com/yangzongzhuan/RuoYi-Vue3` 返回 404，已不再作为有效上游地址；后续以本基线为准。

## 7. 当前状态与下一步

- 阶段 1：Completed（路线确认）
- 阶段 2：InProgress（现状和上游事实核对）
- 阶段 5：InProgress（迁移边界基线）
- 阶段 7：InProgress（Wave 01 三端工程与业务入口）
- 阶段 8：NotStarted（审查验证）
- 阶段 9：NotStarted（用户验收）
- 阶段 10：NotStarted（上线就绪）

下一步必须完成：三端依赖/静态检查授权、后端/前端模块映射、API 契约对照、录音链路迁移任务包；在用户验收前不移动或删除 `frontend/`，不直接覆盖现有后端模块。
